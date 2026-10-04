package com.nexa.task;

/** PRD section 26. */
public enum TaskFilter {
    ALL,
    MINE,
    /** Assigned to someone other than the viewer. */
    TEAM,
    OVERDUE,
    COMPLETED,
    PENDING_APPROVAL,
    /** AI suggestions nobody has accepted or dismissed yet. */
    SUGGESTED
}
