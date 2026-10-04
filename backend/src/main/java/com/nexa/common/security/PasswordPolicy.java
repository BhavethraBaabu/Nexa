package com.nexa.common.security;

/**
 * Password length limits. The maximum keeps inputs within BCrypt's 72-byte limit
 * even for multi-byte characters.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 10;
    public static final int MAX_LENGTH = 64;
    public static final String MESSAGE = "Password must be between 10 and 64 characters";

    private PasswordPolicy() {
    }
}
