package com.nexa.action;

import com.nexa.common.persistence.AssignedIdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** An action the AI proposed. Nothing happens outside Nexa until a person approves it (PRD section 4.1). */
@Entity
@Table(name = "ai_actions")
public class AiAction extends AssignedIdEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "meeting_id", nullable = false, updatable = false)
    private UUID meetingId;

    @Column(name = "task_id", updatable = false)
    private UUID taskId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, updatable = false, length = 30)
    private ActionType actionType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ActionStatus status;

    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 120)
    private String idempotencyKey;

    @Column(name = "requested_by", nullable = false, updatable = false)
    private UUID requestedBy;

    @Column(name = "approved_by")
    private UUID approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "executed_at")
    private Instant executedAt;

    @Column(name = "error_code", length = 40)
    private String errorCode;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(columnDefinition = "text")
    private String result;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AiAction() {
    }

    public static AiAction suggest(UUID organizationId, UUID meetingId, UUID taskId, ActionType type, UUID requestedBy,
                                   Instant now) {
        AiAction action = new AiAction();
        action.assignNewId();
        action.organizationId = organizationId;
        action.meetingId = meetingId;
        action.taskId = taskId;
        action.actionType = type;
        action.status = ActionStatus.PENDING;
        action.idempotencyKey = idempotencyKey(meetingId, taskId, type);
        action.requestedBy = requestedBy;
        action.createdAt = now;
        action.updatedAt = now;
        return action;
    }

    /** PRD section 52: meetingId + taskId + actionType. */
    public static String idempotencyKey(UUID meetingId, UUID taskId, ActionType type) {
        return meetingId + ":" + (taskId == null ? "meeting" : taskId) + ":" + type;
    }

    void approve(UUID approverId, Instant now) {
        status = ActionStatus.APPROVED;
        approvedBy = approverId;
        approvedAt = now;
        nextAttemptAt = now;
        updatedAt = now;
    }

    void reject(UUID userId, Instant now) {
        status = ActionStatus.REJECTED;
        approvedBy = userId;
        approvedAt = now;
        updatedAt = now;
    }

    void complete(String result, Instant now) {
        status = ActionStatus.COMPLETED;
        this.result = result;
        executedAt = now;
        errorCode = null;
        errorMessage = null;
        nextAttemptAt = null;
        updatedAt = now;
    }

    /** Schedules another automatic attempt after {@code delayFrom(now)}. */
    void scheduleRetry(String code, String message, Instant nextAttempt, Instant now) {
        status = ActionStatus.APPROVED;
        errorCode = code;
        errorMessage = message;
        nextAttemptAt = nextAttempt;
        updatedAt = now;
    }

    /** Gives up until a person retries (the dead-letter state). */
    void fail(String code, String message, Instant now) {
        status = ActionStatus.FAILED;
        errorCode = code;
        errorMessage = message;
        nextAttemptAt = null;
        updatedAt = now;
    }

    void manualRetry(Instant now) {
        status = ActionStatus.APPROVED;
        attempts = 0;
        nextAttemptAt = now;
        updatedAt = now;
    }

    public UUID getOrganizationId() { return organizationId; }
    public UUID getMeetingId() { return meetingId; }
    public UUID getTaskId() { return taskId; }
    public ActionType getActionType() { return actionType; }
    public ActionStatus getStatus() { return status; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public UUID getRequestedBy() { return requestedBy; }
    public UUID getApprovedBy() { return approvedBy; }
    public Instant getApprovedAt() { return approvedAt; }
    public int getAttempts() { return attempts; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public Instant getExecutedAt() { return executedAt; }
    public String getErrorCode() { return errorCode; }
    public String getErrorMessage() { return errorMessage; }
    public String getResult() { return result; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
