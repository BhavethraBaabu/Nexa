package com.nexa.task.dto;

import com.nexa.integration.IntegrationProvider;
import com.nexa.meeting.dto.ConfidenceLevel;
import com.nexa.task.DeadlineStatus;
import com.nexa.task.OwnerStatus;
import com.nexa.task.Priority;
import com.nexa.task.TaskStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** PRD section 26: task, owner, priority, deadline, source meeting, status, external links. */
public record TaskResponse(
        UUID id,
        String title,
        String description,
        Person owner,
        String ownerName,
        OwnerStatus ownerStatus,
        Priority priority,
        TaskStatus status,
        LocalDate deadline,
        String deadlineText,
        DeadlineStatus deadlineStatus,
        boolean overdue,
        BigDecimal confidence,
        ConfidenceLevel confidenceLevel,
        String evidence,
        MeetingRef meeting,
        int pendingActions,
        List<ExternalLink> externalLinks,
        boolean canEdit,
        Person editedBy,
        Instant editedAt,
        Instant createdAt
) {

    public record Person(UUID id, String name) {
    }

    public record MeetingRef(UUID id, String title, LocalDate meetingDate) {
    }

    public record ExternalLink(IntegrationProvider provider, String id, String url) {
    }
}
