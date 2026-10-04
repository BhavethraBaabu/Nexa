package com.nexa.risk;

import com.nexa.common.persistence.AssignedIdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "risks")
public class Risk extends AssignedIdEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "meeting_id", nullable = false, updatable = false)
    private UUID meetingId;

    @Column(name = "analysis_id", updatable = false)
    private UUID analysisId;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Severity severity;

    @Column(name = "ai_confidence", nullable = false, precision = 3, scale = 2)
    private BigDecimal aiConfidence;

    @Column(columnDefinition = "text")
    private String evidence;

    @Column(nullable = false)
    private int position;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Risk() {
    }

    public static Risk fromAnalysis(UUID organizationId, UUID meetingId, UUID analysisId, int position,
                                    String description, Severity severity, BigDecimal confidence, String evidence,
                                    Instant now) {
        Risk r = new Risk();
        r.assignNewId();
        r.organizationId = organizationId;
        r.meetingId = meetingId;
        r.analysisId = analysisId;
        r.position = position;
        r.description = description;
        r.severity = severity;
        r.aiConfidence = confidence;
        r.evidence = evidence;
        r.createdAt = now;
        return r;
    }

    public String getDescription() { return description; }
    public Severity getSeverity() { return severity; }
    public BigDecimal getAiConfidence() { return aiConfidence; }
    public String getEvidence() { return evidence; }
}
