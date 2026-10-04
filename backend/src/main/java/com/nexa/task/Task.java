package com.nexa.task;

import com.nexa.common.persistence.AssignedIdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "tasks")
public class Task extends AssignedIdEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "meeting_id", nullable = false, updatable = false)
    private UUID meetingId;

    @Column(name = "analysis_id", updatable = false)
    private UUID analysisId;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "owner_id")
    private UUID ownerId;

    @Column(name = "owner_name", length = 120)
    private String ownerName;

    @Enumerated(EnumType.STRING)
    @Column(name = "owner_status", nullable = false, length = 20)
    private OwnerStatus ownerStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Priority priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TaskStatus status;

    private LocalDate deadline;

    @Column(name = "deadline_text", length = 200)
    private String deadlineText;

    @Enumerated(EnumType.STRING)
    @Column(name = "deadline_status", nullable = false, length = 20)
    private DeadlineStatus deadlineStatus;

    @Column(name = "ai_confidence", nullable = false, precision = 3, scale = 2)
    private BigDecimal aiConfidence;

    @Column(columnDefinition = "text")
    private String evidence;

    @Column(nullable = false)
    private int position;

    @Column(name = "edited_by")
    private UUID editedBy;

    @Column(name = "edited_at")
    private Instant editedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Task() {
    }

    public static Task fromAnalysis(UUID organizationId, UUID meetingId, UUID analysisId, int position,
                                    String title, String description, UUID ownerId, String ownerName,
                                    OwnerStatus ownerStatus, Priority priority, LocalDate deadline,
                                    String deadlineText, DeadlineStatus deadlineStatus, BigDecimal confidence,
                                    String evidence, Instant now) {
        Task task = new Task();
        task.assignNewId();
        task.organizationId = organizationId;
        task.meetingId = meetingId;
        task.analysisId = analysisId;
        task.position = position;
        task.title = title;
        task.description = description;
        task.ownerId = ownerId;
        task.ownerName = ownerName;
        task.ownerStatus = ownerStatus;
        task.priority = priority;
        task.status = TaskStatus.SUGGESTED;
        task.deadline = deadline;
        task.deadlineText = deadlineText;
        task.deadlineStatus = deadlineStatus;
        task.aiConfidence = confidence;
        task.evidence = evidence;
        task.createdAt = now;
        task.updatedAt = now;
        return task;
    }

    /**
     * A person's edit (PRD section 15). Setting an owner or deadline by hand makes it authoritative,
     * replacing whatever the AI extracted.
     */
    public void edit(TaskEdit edit, UUID editorId, Instant now) {
        if (edit.title() != null) {
            title = edit.title();
        }
        if (edit.description() != null) {
            description = edit.description().isBlank() ? null : edit.description();
        }
        if (edit.ownerChanged()) {
            ownerId = edit.ownerId();
            ownerName = edit.ownerName();
            ownerStatus = edit.ownerId() == null ? OwnerStatus.UNASSIGNED : OwnerStatus.RESOLVED;
        }
        if (edit.priority() != null) {
            priority = edit.priority();
        }
        if (edit.deadlineChanged()) {
            deadline = edit.deadline();
            deadlineStatus = edit.deadline() == null ? DeadlineStatus.NONE : DeadlineStatus.RESOLVED;
        }
        if (edit.status() != null) {
            status = edit.status();
        }
        editedBy = editorId;
        editedAt = now;
        updatedAt = now;
    }

    /** Approving an action for a suggested task means a person has accepted the task. */
    public void acceptIfSuggested(Instant now) {
        if (status == TaskStatus.SUGGESTED) {
            status = TaskStatus.OPEN;
            updatedAt = now;
        }
    }

    public boolean isOverdue(LocalDate today) {
        return deadline != null && deadline.isBefore(today) && status != TaskStatus.DONE && status != TaskStatus.CANCELLED;
    }

    public UUID getOrganizationId() { return organizationId; }
    public UUID getMeetingId() { return meetingId; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public UUID getOwnerId() { return ownerId; }
    public String getOwnerName() { return ownerName; }
    public OwnerStatus getOwnerStatus() { return ownerStatus; }
    public Priority getPriority() { return priority; }
    public TaskStatus getStatus() { return status; }
    public LocalDate getDeadline() { return deadline; }
    public String getDeadlineText() { return deadlineText; }
    public DeadlineStatus getDeadlineStatus() { return deadlineStatus; }
    public BigDecimal getAiConfidence() { return aiConfidence; }
    public String getEvidence() { return evidence; }
    public UUID getEditedBy() { return editedBy; }
    public Instant getEditedAt() { return editedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
