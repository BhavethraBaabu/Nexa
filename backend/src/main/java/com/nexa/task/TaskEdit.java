package com.nexa.task;

import java.time.LocalDate;
import java.util.UUID;

/** Validated changes to a task; null fields are unchanged. Owner and deadline use explicit flags so they can be cleared. */
public record TaskEdit(String title, String description, boolean ownerChanged, UUID ownerId, String ownerName,
                       Priority priority, boolean deadlineChanged, LocalDate deadline, TaskStatus status) {
}
