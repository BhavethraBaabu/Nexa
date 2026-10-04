package com.nexa.meeting.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record CreateMeetingRequest(
        @NotBlank(message = "Meeting title is required")
        @Size(max = 200, message = "Meeting title must be at most 200 characters")
        String title,

        @NotNull(message = "Meeting date is required")
        LocalDate meetingDate,

        @Min(value = 1, message = "Duration must be at least 1 minute")
        @Max(value = 1440, message = "Duration must be at most 24 hours")
        Integer durationMinutes,

        @Size(max = 50, message = "A meeting can list at most 50 participants")
        List<@NotBlank(message = "Participant names can't be blank") @Size(max = 120) String> participants,

        @NotBlank(message = "Transcript is required")
        String transcript
) {
}
