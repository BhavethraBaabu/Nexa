package com.nexa.meeting;

import com.nexa.common.persistence.AssignedIdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "meeting_participants")
public class MeetingParticipant extends AssignedIdEntity {

    @Column(name = "meeting_id", nullable = false, updatable = false)
    private UUID meetingId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "user_id")
    private UUID userId;

    @Column(nullable = false)
    private int position;

    protected MeetingParticipant() {
    }

    static MeetingParticipant create(UUID meetingId, String name, UUID userId, int position) {
        MeetingParticipant participant = new MeetingParticipant();
        participant.assignNewId();
        participant.meetingId = meetingId;
        participant.name = name;
        participant.userId = userId;
        participant.position = position;
        return participant;
    }

    UUID getMeetingId() {
        return meetingId;
    }

    public String getName() {
        return name;
    }

    public UUID getUserId() {
        return userId;
    }
}
