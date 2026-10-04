package com.nexa.ai.analysis;

import com.nexa.ai.extraction.ExtractionSchema;
import com.nexa.ai.extraction.ExtractionValidator;
import com.nexa.ai.extraction.MeetingIntelligence;
import com.nexa.ai.extraction.RawExtraction;
import com.nexa.ai.extraction.TranscriptPreprocessor;
import com.nexa.ai.llm.LlmClient;
import com.nexa.ai.llm.LlmException;
import com.nexa.ai.llm.StructuredCompletion;
import com.nexa.ai.prompt.PromptService;
import com.nexa.meeting.Meeting;
import com.nexa.meeting.MeetingParticipant;
import com.nexa.meeting.MeetingParticipantRepository;
import com.nexa.meeting.MeetingRepository;
import com.nexa.user.User;
import com.nexa.user.UserRepository;
import com.nexa.user.UserStatus;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Runs an analysis (PRD section 10): preprocess, extract with the model, validate, resolve
 * entities, score confidence and save. The model call happens outside any database transaction,
 * so a slow provider never holds a connection or a row lock.
 */
@Component
public class AnalysisPipeline {

    private static final Logger log = LoggerFactory.getLogger(AnalysisPipeline.class);
    private static final Map<String, Object> SCHEMA = ExtractionSchema.schema();

    private final AnalysisResultWriter writer;
    private final MeetingRepository meetingRepository;
    private final MeetingParticipantRepository participantRepository;
    private final UserRepository userRepository;
    private final TranscriptPreprocessor preprocessor;
    private final PromptService prompts;
    private final LlmClient llm;
    private final ExtractionValidator validator;
    private final TransactionTemplate readOnlyTx;
    private final MeterRegistry meters;

    public AnalysisPipeline(AnalysisResultWriter writer, MeetingRepository meetingRepository,
                            MeetingParticipantRepository participantRepository, UserRepository userRepository,
                            TranscriptPreprocessor preprocessor, PromptService prompts, LlmClient llm,
                            ExtractionValidator validator, TransactionTemplate transactionTemplate, MeterRegistry meters) {
        this.writer = writer;
        this.meetingRepository = meetingRepository;
        this.participantRepository = participantRepository;
        this.userRepository = userRepository;
        this.preprocessor = preprocessor;
        this.prompts = prompts;
        this.llm = llm;
        this.validator = validator;
        this.readOnlyTx = new TransactionTemplate(transactionTemplate.getTransactionManager());
        this.readOnlyTx.setReadOnly(true);
        this.readOnlyTx.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);
        this.meters = meters;
    }

    @Async("analysisExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAnalysisRequested(MeetingAnalysisRequested event) {
        run(event.analysisId());
    }

    void run(UUID analysisId) {
        MeetingAnalysis analysis = writer.start(analysisId).orElse(null);
        if (analysis == null) {
            return;
        }
        MDC.put("analysisId", analysisId.toString());
        MDC.put("meetingId", analysis.getMeetingId().toString());
        Timer.Sample timer = Timer.start(meters);
        long startedNanos = System.nanoTime();
        try {
            Input input = readOnlyTx.execute(status -> loadInput(analysis));
            if (input == null) {
                writer.fail(analysisId, "MEETING_NOT_FOUND", "The meeting was deleted before analysis finished.", null);
                return;
            }
            String transcript = preprocessor.clean(input.transcript());
            StructuredCompletion completion = llm.completeStructured(
                    prompts.extractionSystemPrompt(),
                    prompts.extractionUserPrompt(input.title(), input.meetingDate(), input.participants(), transcript),
                    SCHEMA);
            RawExtraction raw = validator.parse(completion.json());
            MeetingIntelligence result = validator.validate(raw, transcript, input.meetingDate(), input.members());

            int durationMs = elapsedMs(startedNanos);
            writer.complete(analysisId, result, completion, durationMs);
            meters.counter("nexa.ai.tokens", "type", "input").increment(completion.inputTokens());
            meters.counter("nexa.ai.tokens", "type", "output").increment(completion.outputTokens());
            timer.stop(meters.timer("nexa.ai.analysis", "outcome", "success"));
            log.info("AI analysis completed: {} action items, {} decisions in {} ms",
                    result.actionItems().size(), result.decisions().size(), durationMs);
        } catch (LlmException e) {
            fail(analysisId, e.code().name(), e.userMessage(), startedNanos, timer, e);
        } catch (RuntimeException e) {
            fail(analysisId, LlmException.Code.FAILED.name(), "Nexa couldn't analyze this meeting. Please try again.",
                    startedNanos, timer, e);
        } finally {
            MDC.remove("analysisId");
            MDC.remove("meetingId");
        }
    }

    private void fail(UUID analysisId, String code, String userMessage, long startedNanos, Timer.Sample timer,
                      Exception cause) {
        // Log the cause class and code only: provider messages could echo transcript content.
        log.warn("AI analysis failed with {} ({})", code, cause.getClass().getSimpleName());
        writer.fail(analysisId, code, userMessage, elapsedMs(startedNanos));
        meters.counter("nexa.ai.analysis.failures", "code", code).increment();
        timer.stop(meters.timer("nexa.ai.analysis", "outcome", "failure"));
    }

    private Input loadInput(MeetingAnalysis analysis) {
        Meeting meeting = meetingRepository.findByIdAndOrganizationId(analysis.getMeetingId(), analysis.getOrganizationId())
                .orElse(null);
        if (meeting == null) {
            return null;
        }
        List<String> participants = participantRepository.findByMeetingIdOrderByPositionAsc(meeting.getId()).stream()
                .map(MeetingParticipant::getName)
                .toList();
        List<User> members = userRepository.findByOrganizationIdAndStatusOrderByNameAsc(meeting.getOrganizationId(), UserStatus.ACTIVE);
        return new Input(meeting.getTitle(), meeting.getMeetingDate(), meeting.getTranscript(), participants, members);
    }

    private static int elapsedMs(long startedNanos) {
        return (int) ((System.nanoTime() - startedNanos) / 1_000_000);
    }

    private record Input(String title, LocalDate meetingDate, String transcript, List<String> participants,
                         List<User> members) {
    }
}
