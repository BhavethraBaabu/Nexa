package com.nexa.ai.analysis;

import java.time.Instant;
import java.util.UUID;

public record AnalysisResponse(UUID id, UUID meetingId, AnalysisStatus status, String errorCode, String errorMessage,
                               boolean retryable, String model, String promptVersion, Integer durationMs,
                               Instant createdAt, Instant completedAt) {

    public static AnalysisResponse from(MeetingAnalysis a) {
        return new AnalysisResponse(a.getId(), a.getMeetingId(), a.getStatus(), a.getErrorCode(), a.getErrorMessage(),
                a.isRetryable(), a.getModel(), a.getPromptVersion(), a.getDurationMs(), a.getCreatedAt(), a.getCompletedAt());
    }
}
