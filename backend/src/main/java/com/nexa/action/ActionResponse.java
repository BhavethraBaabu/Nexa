package com.nexa.action;

import com.nexa.integration.IntegrationProvider;
import com.nexa.task.DeadlineStatus;
import com.nexa.task.Priority;
import com.nexa.task.TaskStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * An action as shown for review (PRD sections 15, 16 and 4.4: requester, approver, timestamp,
 * type, integration, result and external ID).
 */
public record ActionResponse(
        UUID id,
        ActionType type,
        ActionStatus status,
        IntegrationProvider provider,
        MeetingRef meeting,
        TaskRef task,
        Person requestedBy,
        Person approvedBy,
        Instant approvedAt,
        Instant executedAt,
        int attempts,
        Instant nextAttemptAt,
        String errorCode,
        String errorMessage,
        boolean retryable,
        External external,
        String result,
        boolean canApprove,
        /** Why this action can't be approved yet (e.g. Jira not connected), or null. */
        String blockedReason,
        Instant createdAt
) {

    public record MeetingRef(UUID id, String title, LocalDate meetingDate) {
    }

    public record TaskRef(UUID id, String title, String ownerName, boolean ownerResolved, Priority priority,
                          LocalDate deadline, DeadlineStatus deadlineStatus, TaskStatus status) {
    }

    public record Person(UUID id, String name) {
    }

    public record External(IntegrationProvider provider, String id, String url) {
    }
}
