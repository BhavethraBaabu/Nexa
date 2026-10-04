package com.nexa.common.api;

import org.springframework.http.HttpStatus;

/**
 * Machine-readable error codes exposed in {@link ApiError#error()}.
 */
public enum ErrorCode {
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST),
    INVALID_TOKEN(HttpStatus.BAD_REQUEST),
    BUSINESS_RULE_VIOLATION(HttpStatus.UNPROCESSABLE_CONTENT),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED),
    FORBIDDEN(HttpStatus.FORBIDDEN),
    NOT_FOUND(HttpStatus.NOT_FOUND),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED),
    CONFLICT(HttpStatus.CONFLICT),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
