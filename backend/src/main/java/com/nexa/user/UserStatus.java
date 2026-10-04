package com.nexa.user;

public enum UserStatus {
    ACTIVE,
    /** Removed from the organization. Kept for referential integrity and audit history. */
    DISABLED
}
