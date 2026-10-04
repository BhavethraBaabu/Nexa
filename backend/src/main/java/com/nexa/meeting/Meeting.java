package com.nexa.meeting;

import com.nexa.common.persistence.AssignedIdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "meetings")
public class Meeting extends AssignedIdEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "meeting_date", nullable = false)
    private LocalDate meetingDate;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @Column(nullable = false, columnDefinition = "text")
    private String transcript;

    @Column(columnDefinition = "text")
    private String summary;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "key_points", nullable = false, columnDefinition = "text[]")
    private List<String> keyPoints = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MeetingStatus status;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(name = "analyzed_at")
    private Instant analyzedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Meeting() {
    }

    public static Meeting create(UUID organizationId, String title, LocalDate meetingDate, Integer durationMinutes,
                                 String transcript, UUID createdBy, Instant now) {
        Meeting meeting = new Meeting();
        meeting.assignNewId();
        meeting.organizationId = organizationId;
        meeting.title = title;
        meeting.meetingDate = meetingDate;
        meeting.durationMinutes = durationMinutes;
        meeting.transcript = transcript;
        meeting.status = MeetingStatus.UPLOADED;
        meeting.createdBy = createdBy;
        meeting.createdAt = now;
        meeting.updatedAt = now;
        return meeting;
    }

    public void updateDetails(String title, LocalDate meetingDate, Integer durationMinutes, Instant now) {
        this.title = title;
        this.meetingDate = meetingDate;
        this.durationMinutes = durationMinutes;
        this.updatedAt = now;
    }

    /** Replacing the transcript invalidates any previous analysis. */
    public void replaceTranscript(String transcript, Instant now) {
        this.transcript = transcript;
        this.summary = null;
        this.keyPoints = new ArrayList<>();
        this.analyzedAt = null;
        this.status = MeetingStatus.UPLOADED;
        this.updatedAt = now;
    }

    public void markProcessing(Instant now) {
        this.status = MeetingStatus.PROCESSING;
        this.updatedAt = now;
    }

    public void markAnalyzed(String summary, List<String> keyPoints, Instant now) {
        this.summary = summary;
        this.keyPoints = new ArrayList<>(keyPoints);
        this.status = MeetingStatus.COMPLETED;
        this.analyzedAt = now;
        this.updatedAt = now;
    }

    /** A failed analysis never touches the transcript or earlier results ("Your transcript is safely stored"). */
    public void markAnalysisFailed(Instant now) {
        this.status = MeetingStatus.FAILED;
        this.updatedAt = now;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public String getTitle() {
        return title;
    }

    public LocalDate getMeetingDate() {
        return meetingDate;
    }

    public Integer getDurationMinutes() {
        return durationMinutes;
    }

    public String getTranscript() {
        return transcript;
    }

    public String getSummary() {
        return summary;
    }

    public List<String> getKeyPoints() {
        return List.copyOf(keyPoints);
    }

    public MeetingStatus getStatus() {
        return status;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public Instant getAnalyzedAt() {
        return analyzedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
