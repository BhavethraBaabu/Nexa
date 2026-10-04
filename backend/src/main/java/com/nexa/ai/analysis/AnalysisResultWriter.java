package com.nexa.ai.analysis;

import com.nexa.action.ActionGenerator;
import com.nexa.ai.extraction.MeetingIntelligence;
import com.nexa.ai.llm.StructuredCompletion;
import com.nexa.audit.AuditAction;
import com.nexa.audit.AuditService;
import com.nexa.decision.Decision;
import com.nexa.decision.DecisionRepository;
import com.nexa.meeting.Meeting;
import com.nexa.meeting.MeetingQuestion;
import com.nexa.meeting.MeetingQuestionRepository;
import com.nexa.meeting.MeetingRepository;
import com.nexa.risk.Risk;
import com.nexa.risk.RiskRepository;
import com.nexa.task.Task;
import com.nexa.task.TaskRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Persists analysis outcomes. Each method is one transaction, so results appear all at once or
 * not at all. A new analysis replaces the previous AI suggestions for the meeting; tasks a person
 * has already accepted (anything no longer SUGGESTED) are kept.
 * <p>
 * REQUIRES_NEW because the pipeline may run right after the requesting transaction commits, on
 * the same thread; joining that finished transaction would fail.
 */
@Component
class AnalysisResultWriter {

    private final MeetingAnalysisRepository analysisRepository;
    private final MeetingRepository meetingRepository;
    private final TaskRepository taskRepository;
    private final DecisionRepository decisionRepository;
    private final RiskRepository riskRepository;
    private final MeetingQuestionRepository questionRepository;
    private final AuditService auditService;
    private final ActionGenerator actionGenerator;
    private final Clock clock;

    AnalysisResultWriter(MeetingAnalysisRepository analysisRepository, MeetingRepository meetingRepository,
                         TaskRepository taskRepository, DecisionRepository decisionRepository,
                         RiskRepository riskRepository, MeetingQuestionRepository questionRepository,
                         AuditService auditService, ActionGenerator actionGenerator, Clock clock) {
        this.analysisRepository = analysisRepository;
        this.meetingRepository = meetingRepository;
        this.taskRepository = taskRepository;
        this.decisionRepository = decisionRepository;
        this.riskRepository = riskRepository;
        this.questionRepository = questionRepository;
        this.auditService = auditService;
        this.actionGenerator = actionGenerator;
        this.clock = clock;
    }

    /** Marks the analysis as started. Returns empty if it was already finished or deleted. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<MeetingAnalysis> start(UUID analysisId) {
        return analysisRepository.findById(analysisId)
                .filter(a -> !a.isFinished())
                .map(a -> {
                    a.start(clock.instant());
                    return a;
                });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(UUID analysisId, MeetingIntelligence result, StructuredCompletion completion, int durationMs) {
        MeetingAnalysis analysis = analysisRepository.findById(analysisId).orElse(null);
        Meeting meeting = analysis == null ? null
                : meetingRepository.findByIdAndOrganizationId(analysis.getMeetingId(), analysis.getOrganizationId()).orElse(null);
        if (analysis == null || meeting == null || analysis.isFinished()) {
            return; // Meeting deleted mid-analysis, or the job was already timed out.
        }
        Instant now = clock.instant();
        UUID org = meeting.getOrganizationId();
        UUID meetingId = meeting.getId();

        taskRepository.deleteSuggestedForMeeting(meetingId);
        decisionRepository.deleteByMeetingId(meetingId);
        riskRepository.deleteByMeetingId(meetingId);
        questionRepository.deleteByMeetingId(meetingId);

        int position = 0;
        java.util.List<Task> newTasks = new java.util.ArrayList<>();
        for (MeetingIntelligence.ActionItem a : result.actionItems()) {
            newTasks.add(taskRepository.save(Task.fromAnalysis(org, meetingId, analysisId, position++, a.title(), a.description(),
                    a.ownerId(), a.ownerName(), a.ownerStatus(), a.priority(), a.deadline(), a.deadlineText(),
                    a.deadlineStatus(), a.confidence(), a.evidence(), now)));
        }
        actionGenerator.replaceSuggestions(org, meetingId, newTasks, analysis.getRequestedBy(), now);
        position = 0;
        for (MeetingIntelligence.DecisionItem d : result.decisions()) {
            decisionRepository.save(Decision.fromAnalysis(org, meetingId, analysisId, position++, d.decision(), d.context(),
                    d.confidence(), d.evidence(), now));
        }
        position = 0;
        for (MeetingIntelligence.RiskItem r : result.risks()) {
            riskRepository.save(Risk.fromAnalysis(org, meetingId, analysisId, position++, r.description(), r.severity(),
                    r.confidence(), r.evidence(), now));
        }
        position = 0;
        for (MeetingIntelligence.QuestionItem q : result.questions()) {
            questionRepository.save(MeetingQuestion.fromAnalysis(org, meetingId, analysisId, position++, q.question(),
                    q.confidence(), q.evidence(), now));
        }

        meeting.markAnalyzed(result.summary(), result.keyPoints(), now);
        analysis.complete(completion.model(), completion.inputTokens(), completion.outputTokens(), durationMs,
                completion.json(), now);
        auditService.record(org, analysis.getRequestedBy(), AuditAction.MEETING_ANALYZED, "MEETING", meetingId, Map.of(
                "analysisId", analysisId.toString(),
                "actionItems", result.actionItems().size(),
                "decisions", result.decisions().size(),
                "risks", result.risks().size(),
                "questions", result.questions().size()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(UUID analysisId, String errorCode, String userMessage, Integer durationMs) {
        analysisRepository.findById(analysisId).filter(a -> !a.isFinished()).ifPresent(analysis -> {
            Instant now = clock.instant();
            analysis.fail(errorCode, userMessage, durationMs, now);
            meetingRepository.findByIdAndOrganizationId(analysis.getMeetingId(), analysis.getOrganizationId())
                    .ifPresent(meeting -> meeting.markAnalysisFailed(now));
            auditService.record(analysis.getOrganizationId(), analysis.getRequestedBy(), AuditAction.MEETING_ANALYSIS_FAILED,
                    "MEETING", analysis.getMeetingId(), Map.of("analysisId", analysisId.toString(), "errorCode", errorCode));
        });
    }
}
