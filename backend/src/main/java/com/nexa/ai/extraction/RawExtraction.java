package com.nexa.ai.extraction;

import java.util.List;

/** The model's output exactly as returned, before validation. Any field may be missing or malformed. */
public record RawExtraction(
        String summary,
        List<String> keyPoints,
        List<ActionItem> actionItems,
        List<DecisionItem> decisions,
        List<RiskItem> risks,
        List<QuestionItem> unresolvedQuestions
) {

    public record ActionItem(String title, String description, String ownerName, String deadlineText, String deadline,
                             String priority, Double confidence, String evidence) {
    }

    public record DecisionItem(String decision, String context, Double confidence, String evidence) {
    }

    public record RiskItem(String description, String severity, Double confidence, String evidence) {
    }

    public record QuestionItem(String question, Double confidence, String evidence) {
    }
}
