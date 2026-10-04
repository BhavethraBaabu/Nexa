package com.nexa.meeting.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/** Partial update: fields left null are unchanged. Changing the transcript clears earlier AI results. */
public record UpdateMeetingRequest(
        @Size(min = 1, max = 200, message = "Meeting title must be 1-200 characters") String title,
        LocalDate meetingDate,
        @Min(1) @Max(1440) Integer durationMinutes,
        @Size(max = 50) List<@NotBlank @Size(max = 120) String> participants,
        @Size(min = 1, message = "Transcript can't be empty") String transcript
) {
}
