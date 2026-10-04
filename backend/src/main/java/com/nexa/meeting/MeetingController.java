package com.nexa.meeting;

import com.nexa.common.api.PageResponse;
import com.nexa.common.exception.InvalidRequestException;
import com.nexa.common.security.AuthenticatedUser;
import com.nexa.meeting.dto.CreateMeetingRequest;
import com.nexa.meeting.dto.MeetingDetailResponse;
import com.nexa.meeting.dto.MeetingSummaryResponse;
import com.nexa.meeting.dto.TranscriptTextResponse;
import com.nexa.meeting.dto.UpdateMeetingRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/meetings")
public class MeetingController {

    private final MeetingService meetingService;
    private final TranscriptFileExtractor transcriptFileExtractor;

    public MeetingController(MeetingService meetingService, TranscriptFileExtractor transcriptFileExtractor) {
        this.meetingService = meetingService;
        this.transcriptFileExtractor = transcriptFileExtractor;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MeetingDetailResponse create(@AuthenticationPrincipal AuthenticatedUser current,
                                        @Valid @RequestBody CreateMeetingRequest request) {
        return meetingService.create(current, request);
    }

    @GetMapping
    public PageResponse<MeetingSummaryResponse> list(@AuthenticationPrincipal AuthenticatedUser current,
                                                     @RequestParam(defaultValue = "0") int page,
                                                     @RequestParam(defaultValue = "20") int size) {
        return meetingService.list(current, page, size);
    }

    @GetMapping("/{id}")
    public MeetingDetailResponse get(@AuthenticationPrincipal AuthenticatedUser current, @PathVariable UUID id) {
        return meetingService.get(current, id);
    }

    @PatchMapping("/{id}")
    public MeetingDetailResponse update(@AuthenticationPrincipal AuthenticatedUser current, @PathVariable UUID id,
                                        @Valid @RequestBody UpdateMeetingRequest request) {
        return meetingService.update(current, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthenticatedUser current, @PathVariable UUID id) {
        meetingService.delete(current, id);
    }

    /**
     * Extracts text from an uploaded TXT, PDF or DOCX transcript so the user can review it before
     * creating the meeting. Nothing is stored.
     */
    @PostMapping(path = "/transcript-file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public TranscriptTextResponse extractTranscript(@RequestPart("file") MultipartFile file) {
        try {
            String text = transcriptFileExtractor.extract(file.getOriginalFilename(), file.getBytes());
            return new TranscriptTextResponse(file.getOriginalFilename(), text, text.length());
        } catch (IOException e) {
            throw new InvalidRequestException("The upload couldn't be read. Please try again.");
        }
    }
}
