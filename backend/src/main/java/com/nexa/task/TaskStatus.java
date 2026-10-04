package com.nexa.task;

/** Task lifecycle. AI-created tasks start as SUGGESTED until a person reviews them (Phases 4-5). */
public enum TaskStatus {
    SUGGESTED,
    OPEN,
    IN_PROGRESS,
    DONE,
    CANCELLED
}
