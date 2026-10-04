package com.nexa.meeting.dto;

import com.nexa.ai.analysis.AnalysisResponse;
import com.nexa.meeting.MeetingStatus;
import com.nexa.risk.Severity;
import com.nexa.task.DeadlineStatus;
import com.nexa.task.OwnerStatus;
import com.nexa.task.Priority;
import com.nexa.task.TaskStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record MeetingDetailResponse(
        UUID id,
        String title,
        LocalDate meetingDate,
        Integer durationMinutes,
        MeetingStatus status,
        String transcript,
        List<Participant> participants,
        Person createdBy,
        Instant createdAt,
        Instant updatedAt,
        boolean canEdit,
        Analysis analysis
) {

    public record Person(UUID id, String name) {
    }

    public record Participant(String name, UUID userId) {
    }

    /** Null until the meeting has been analyzed at least once. */
    public record Analysis(
            AnalysisResponse latestRun,
            String summary,
            List<String> keyPoints,
            Instant analyzedAt,
            List<ActionItem> actionItems,
            List<DecisionView> decisions,
            List<RiskView> risks,
            List<QuestionView> questions
    ) {
    }

    public record ActionItem(UUID id, String title, String description, Person owner, String ownerName,
                             OwnerStatus ownerStatus, Priority priority, TaskStatus status, LocalDate deadline,
                             String deadlineText, DeadlineStatus deadlineStatus, BigDecimal confidence,
                             ConfidenceLevel confidenceLevel, String evidence) {
    }

    public record DecisionView(UUID id, String decision, String context, BigDecimal confidence,
                               ConfidenceLevel confidenceLevel, String evidence) {
    }

    public record RiskView(UUID id, String description, Severity severity, BigDecimal confidence,
                           ConfidenceLevel confidenceLevel, String evidence) {
    }

    public record QuestionView(UUID id, String question, String status, BigDecimal confidence,
                               ConfidenceLevel confidenceLevel, String evidence) {
    }
}
