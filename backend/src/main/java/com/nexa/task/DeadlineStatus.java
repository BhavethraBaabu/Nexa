package com.nexa.task;

public enum DeadlineStatus {
    /** No deadline was mentioned. */
    NONE,
    /** Resolved unambiguously against the meeting date. */
    RESOLVED,
    /** A deadline was mentioned but is vague ("soon", "next Friday"); a person should confirm it. */
    NEEDS_REVIEW
}
