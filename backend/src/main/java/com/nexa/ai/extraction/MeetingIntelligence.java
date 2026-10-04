package com.nexa.ai.extraction;

import com.nexa.risk.Severity;
import com.nexa.task.DeadlineStatus;
import com.nexa.task.OwnerStatus;
import com.nexa.task.Priority;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Validated, resolved analysis output, ready to persist. */
public record MeetingIntelligence(
        String summary,
        List<String> keyPoints,
        List<ActionItem> actionItems,
        List<DecisionItem> decisions,
        List<RiskItem> risks,
        List<QuestionItem> questions
) {

    public record ActionItem(String title, String description, UUID ownerId, String ownerName, OwnerStatus ownerStatus,
                             Priority priority, LocalDate deadline, String deadlineText, DeadlineStatus deadlineStatus,
                             BigDecimal confidence, String evidence) {
    }

    public record DecisionItem(String decision, String context, BigDecimal confidence, String evidence) {
    }

    public record RiskItem(String description, Severity severity, BigDecimal confidence, String evidence) {
    }

    public record QuestionItem(String question, BigDecimal confidence, String evidence) {
    }
}
