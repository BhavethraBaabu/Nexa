package com.nexa.ai.analysis;

import com.nexa.ai.llm.LlmException;
import com.nexa.common.persistence.AssignedIdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** One run of the AI pipeline over a meeting (PRD section 35). Kept as history; never updated after it finishes. */
@Entity
@Table(name = "meeting_analyses")
public class MeetingAnalysis extends AssignedIdEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "meeting_id", nullable = false, updatable = false)
    private UUID meetingId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AnalysisStatus status;

    @Column(name = "requested_by", nullable = false, updatable = false)
    private UUID requestedBy;

    @Column(length = 100)
    private String model;

    @Column(name = "prompt_version", nullable = false, length = 40)
    private String promptVersion;

    @Column(name = "input_tokens")
    private Integer inputTokens;

    @Column(name = "output_tokens")
    private Integer outputTokens;

    @Column(name = "duration_ms")
    private Integer durationMs;

    @Column(name = "error_code", length = 40)
    private String errorCode;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "raw_output", columnDefinition = "text")
    private String rawOutput;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected MeetingAnalysis() {
    }

    static MeetingAnalysis queue(UUID organizationId, UUID meetingId, UUID requestedBy, String promptVersion, Instant now) {
        MeetingAnalysis analysis = new MeetingAnalysis();
        analysis.assignNewId();
        analysis.organizationId = organizationId;
        analysis.meetingId = meetingId;
        analysis.requestedBy = requestedBy;
        analysis.promptVersion = promptVersion;
        analysis.status = AnalysisStatus.QUEUED;
        analysis.createdAt = now;
        return analysis;
    }

    void start(Instant now) {
        status = AnalysisStatus.PROCESSING;
        startedAt = now;
    }

    void complete(String model, long inputTokens, long outputTokens, int durationMs, String rawOutput, Instant now) {
        status = AnalysisStatus.COMPLETED;
        this.model = model;
        this.inputTokens = Math.toIntExact(inputTokens);
        this.outputTokens = Math.toIntExact(outputTokens);
        this.durationMs = durationMs;
        this.rawOutput = rawOutput;
        completedAt = now;
    }

    void fail(String errorCode, String errorMessage, Integer durationMs, Instant now) {
        status = AnalysisStatus.FAILED;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.durationMs = durationMs;
        completedAt = now;
    }

    boolean isFinished() {
        return status == AnalysisStatus.COMPLETED || status == AnalysisStatus.FAILED;
    }

    /** Whether trying again might succeed, so the UI can offer Retry. */
    public boolean isRetryable() {
        if (status != AnalysisStatus.FAILED || errorCode == null) {
            return false;
        }
        try {
            return LlmException.Code.valueOf(errorCode).retryable();
        } catch (IllegalArgumentException e) {
            return true;
        }
    }

    public UUID getOrganizationId() { return organizationId; }
    public UUID getMeetingId() { return meetingId; }
    public AnalysisStatus getStatus() { return status; }
    public UUID getRequestedBy() { return requestedBy; }
    public String getModel() { return model; }
    public String getPromptVersion() { return promptVersion; }
    public Integer getInputTokens() { return inputTokens; }
    public Integer getOutputTokens() { return outputTokens; }
    public Integer getDurationMs() { return durationMs; }
    public String getErrorCode() { return errorCode; }
    public String getErrorMessage() { return errorMessage; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
}
