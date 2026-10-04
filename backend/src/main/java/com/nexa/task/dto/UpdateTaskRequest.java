package com.nexa.task.dto;

import com.nexa.task.Priority;
import com.nexa.task.TaskStatus;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Partial update (PRD section 15: title, description, owner, priority, deadline; plus status).
 * Null fields are unchanged. Use {@code clearOwner} / {@code clearDeadline} to remove a value.
 */
public record UpdateTaskRequest(
        @Size(min = 1, max = 300, message = "Title must be 1-300 characters") String title,
        @Size(max = 4000, message = "Description must be at most 4000 characters") String description,
        UUID ownerId,
        Boolean clearOwner,
        Priority priority,
        LocalDate deadline,
        Boolean clearDeadline,
        TaskStatus status
) {
}
