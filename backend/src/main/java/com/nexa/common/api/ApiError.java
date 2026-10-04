package com.nexa.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * Standard error body returned by every API endpoint (PRD section 30).
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        String correlationId,
        List<FieldViolation> violations
) {

    public record FieldViolation(String field, String message) {
    }

    public static ApiError of(ErrorCode code, String message, String path, String correlationId) {
        return new ApiError(Instant.now(), code.status().value(), code.name(), message, path, correlationId, List.of());
    }

    public static ApiError validation(String message, String path, String correlationId, List<FieldViolation> violations) {
        ErrorCode code = ErrorCode.VALIDATION_ERROR;
        return new ApiError(Instant.now(), code.status().value(), code.name(), message, path, correlationId, violations);
    }
}
