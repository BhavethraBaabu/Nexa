package com.nexa.meeting.dto;

import com.nexa.meeting.Meeting;
import com.nexa.meeting.MeetingStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record MeetingSummaryResponse(UUID id, String title, LocalDate meetingDate, Integer durationMinutes,
                                     MeetingStatus status, long participantCount, Instant createdAt) {

    public static MeetingSummaryResponse from(Meeting meeting, long participantCount) {
        return new MeetingSummaryResponse(meeting.getId(), meeting.getTitle(), meeting.getMeetingDate(),
                meeting.getDurationMinutes(), meeting.getStatus(), participantCount, meeting.getCreatedAt());
    }
}
