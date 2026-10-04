package com.nexa.meeting;

import com.nexa.common.persistence.AssignedIdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** An unresolved question raised in a meeting (PRD section 11.5). */
@Entity
@Table(name = "questions")
public class MeetingQuestion extends AssignedIdEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "meeting_id", nullable = false, updatable = false)
    private UUID meetingId;

    @Column(name = "analysis_id", updatable = false)
    private UUID analysisId;

    @Column(nullable = false, columnDefinition = "text")
    private String question;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "ai_confidence", nullable = false, precision = 3, scale = 2)
    private BigDecimal aiConfidence;

    @Column(columnDefinition = "text")
    private String evidence;

    @Column(nullable = false)
    private int position;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected MeetingQuestion() {
    }

    public static MeetingQuestion fromAnalysis(UUID organizationId, UUID meetingId, UUID analysisId, int position,
                                               String question, BigDecimal confidence, String evidence, Instant now) {
        MeetingQuestion q = new MeetingQuestion();
        q.assignNewId();
        q.organizationId = organizationId;
        q.meetingId = meetingId;
        q.analysisId = analysisId;
        q.position = position;
        q.question = question;
        q.status = "UNRESOLVED";
        q.aiConfidence = confidence;
        q.evidence = evidence;
        q.createdAt = now;
        return q;
    }

    public String getQuestion() { return question; }
    public String getStatus() { return status; }
    public BigDecimal getAiConfidence() { return aiConfidence; }
    public String getEvidence() { return evidence; }
}
