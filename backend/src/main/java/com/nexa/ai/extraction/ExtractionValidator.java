package com.nexa.ai.extraction;

import com.nexa.ai.llm.LlmException;
import com.nexa.ai.resolution.DeadlineResolver;
import com.nexa.risk.Severity;
import com.nexa.task.OwnerStatus;
import com.nexa.task.Priority;
import com.nexa.user.MemberNameResolver;
import com.nexa.user.User;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Turns raw model output into trustworthy data (PRD sections 10-14):
 * <ul>
 *   <li>parses the JSON and rejects output without a summary;</li>
 *   <li>drops empty or duplicate items and clamps sizes and confidence to valid ranges;</li>
 *   <li>checks each evidence quote against the transcript, capping the confidence of items whose
 *       quote cannot be found at {@value #UNVERIFIED_CONFIDENCE_CAP} (LOW);</li>
 *   <li>resolves owners against organization members, never inventing one;</li>
 *   <li>resolves deadlines against the meeting date.</li>
 * </ul>
 */
@Component
public class ExtractionValidator {

    static final double UNVERIFIED_CONFIDENCE_CAP = 0.5;
    private static final double DEFAULT_CONFIDENCE = 0.5;
    private static final int MAX_ITEMS = 50;
    private static final int MAX_KEY_POINTS = 10;

    private final ObjectMapper objectMapper;
    private final MemberNameResolver memberNameResolver;
    private final DeadlineResolver deadlineResolver;

    public ExtractionValidator(ObjectMapper objectMapper, MemberNameResolver memberNameResolver,
                               DeadlineResolver deadlineResolver) {
        this.objectMapper = objectMapper.rebuild()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
        this.memberNameResolver = memberNameResolver;
        this.deadlineResolver = deadlineResolver;
    }

    public RawExtraction parse(String json) {
        try {
            RawExtraction raw = objectMapper.readValue(json, RawExtraction.class);
            if (raw == null || isBlank(raw.summary())) {
                throw new LlmException(LlmException.Code.INVALID_RESPONSE, "missing summary");
            }
            return raw;
        } catch (JacksonException e) {
            throw new LlmException(LlmException.Code.INVALID_RESPONSE, "unparseable JSON", e);
        }
    }

    public MeetingIntelligence validate(RawExtraction raw, String transcript, LocalDate meetingDate, List<User> members) {
        String canonicalTranscript = TranscriptPreprocessor.canonical(transcript);

        List<MeetingIntelligence.ActionItem> actions = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (RawExtraction.ActionItem item : nonNull(raw.actionItems())) {
            String title = clip(item.title(), 300);
            if (title == null || !seen.add(TranscriptPreprocessor.canonical(title)) || actions.size() >= MAX_ITEMS) {
                continue;
            }
            String ownerName = clip(item.ownerName(), 120);
            UUID ownerId = null;
            OwnerStatus ownerStatus = OwnerStatus.UNASSIGNED;
            if (ownerName != null) {
                ownerStatus = OwnerStatus.UNRESOLVED;
                if (memberNameResolver.resolve(ownerName, members) instanceof MemberNameResolver.Resolution.Matched(User user)) {
                    ownerId = user.getId();
                    ownerStatus = OwnerStatus.RESOLVED;
                }
            }
            String deadlineText = clip(item.deadlineText(), 200);
            DeadlineResolver.Resolved deadline = deadlineResolver.resolve(deadlineText, parseDate(item.deadline()), meetingDate);
            String evidence = clip(item.evidence(), 1000);

            actions.add(new MeetingIntelligence.ActionItem(title, clip(item.description(), 4000), ownerId, ownerName,
                    ownerStatus, parsePriority(item.priority()), deadline.deadline(), deadlineText, deadline.status(),
                    confidence(item.confidence(), evidence, canonicalTranscript), evidence));
        }

        List<MeetingIntelligence.DecisionItem> decisions = new ArrayList<>();
        seen.clear();
        for (RawExtraction.DecisionItem item : nonNull(raw.decisions())) {
            String text = clip(item.decision(), 2000);
            if (text == null || !seen.add(TranscriptPreprocessor.canonical(text)) || decisions.size() >= MAX_ITEMS) {
                continue;
            }
            String evidence = clip(item.evidence(), 1000);
            decisions.add(new MeetingIntelligence.DecisionItem(text, clip(item.context(), 2000),
                    confidence(item.confidence(), evidence, canonicalTranscript), evidence));
        }

        List<MeetingIntelligence.RiskItem> risks = new ArrayList<>();
        seen.clear();
        for (RawExtraction.RiskItem item : nonNull(raw.risks())) {
            String text = clip(item.description(), 2000);
            if (text == null || !seen.add(TranscriptPreprocessor.canonical(text)) || risks.size() >= MAX_ITEMS) {
                continue;
            }
            String evidence = clip(item.evidence(), 1000);
            risks.add(new MeetingIntelligence.RiskItem(text, parseSeverity(item.severity()),
                    confidence(item.confidence(), evidence, canonicalTranscript), evidence));
        }

        List<MeetingIntelligence.QuestionItem> questions = new ArrayList<>();
        seen.clear();
        for (RawExtraction.QuestionItem item : nonNull(raw.unresolvedQuestions())) {
            String text = clip(item.question(), 2000);
            if (text == null || !seen.add(TranscriptPreprocessor.canonical(text)) || questions.size() >= MAX_ITEMS) {
                continue;
            }
            String evidence = clip(item.evidence(), 1000);
            questions.add(new MeetingIntelligence.QuestionItem(text,
                    confidence(item.confidence(), evidence, canonicalTranscript), evidence));
        }

        List<String> keyPoints = nonNull(raw.keyPoints()).stream()
                .map(p -> clip(p, 500))
                .filter(p -> p != null)
                .distinct()
                .limit(MAX_KEY_POINTS)
                .toList();

        return new MeetingIntelligence(clip(raw.summary(), 4000), keyPoints, actions, decisions, risks, questions);
    }

    private static BigDecimal confidence(Double reported, String evidence, String canonicalTranscript) {
        double value = reported == null || reported.isNaN() ? DEFAULT_CONFIDENCE : Math.clamp(reported, 0.0, 1.0);
        if (!evidenceFound(evidence, canonicalTranscript)) {
            value = Math.min(value, UNVERIFIED_CONFIDENCE_CAP);
        }
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    static boolean evidenceFound(String evidence, String canonicalTranscript) {
        if (isBlank(evidence)) {
            return false;
        }
        String quote = TranscriptPreprocessor.canonical(evidence);
        // Very short quotes ("yes") would match almost anything and prove nothing.
        return quote.length() >= 8 && canonicalTranscript.contains(quote);
    }

    private static Priority parsePriority(String value) {
        try {
            return value == null ? Priority.MEDIUM : Priority.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return Priority.MEDIUM;
        }
    }

    private static Severity parseSeverity(String value) {
        try {
            return value == null ? Severity.MEDIUM : Severity.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return Severity.MEDIUM;
        }
    }

    private static LocalDate parseDate(String value) {
        if (isBlank(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String clip(String value, int max) {
        if (isBlank(value)) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static <T> List<T> nonNull(List<T> list) {
        return list == null ? List.of() : list.stream().filter(item -> item != null).toList();
    }
}
