package com.nexa.task;

public enum OwnerStatus {
    /** Matched exactly one organization member. */
    RESOLVED,
    /** A name was mentioned but matched no member, or several. Shown to users; never guessed. */
    UNRESOLVED,
    /** The transcript did not say who owns it. */
    UNASSIGNED
}
