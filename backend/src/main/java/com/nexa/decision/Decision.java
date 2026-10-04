package com.nexa.decision;

import com.nexa.common.persistence.AssignedIdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "decisions")
public class Decision extends AssignedIdEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "meeting_id", nullable = false, updatable = false)
    private UUID meetingId;

    @Column(name = "analysis_id", updatable = false)
    private UUID analysisId;

    @Column(nullable = false, columnDefinition = "text")
    private String decision;

    @Column(columnDefinition = "text")
    private String context;

    @Column(name = "ai_confidence", nullable = false, precision = 3, scale = 2)
    private BigDecimal aiConfidence;

    @Column(columnDefinition = "text")
    private String evidence;

    @Column(nullable = false)
    private int position;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Decision() {
    }

    public static Decision fromAnalysis(UUID organizationId, UUID meetingId, UUID analysisId, int position,
                                        String decision, String context, BigDecimal confidence, String evidence,
                                        Instant now) {
        Decision d = new Decision();
        d.assignNewId();
        d.organizationId = organizationId;
        d.meetingId = meetingId;
        d.analysisId = analysisId;
        d.position = position;
        d.decision = decision;
        d.context = context;
        d.aiConfidence = confidence;
        d.evidence = evidence;
        d.createdAt = now;
        return d;
    }

    public UUID getMeetingId() { return meetingId; }
    public String getDecision() { return decision; }
    public String getContext() { return context; }
    public BigDecimal getAiConfidence() { return aiConfidence; }
    public String getEvidence() { return evidence; }
    public Instant getCreatedAt() { return createdAt; }
}
