package com.nexa.common.exception;

import com.nexa.common.api.ErrorCode;

/**
 * Base type for expected, domain-level failures. The message is safe to show to API clients.
 */
public abstract class NexaException extends RuntimeException {

    private final ErrorCode errorCode;

    protected NexaException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
