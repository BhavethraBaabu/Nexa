package com.nexa.ai.analysis;

import com.nexa.ai.prompt.PromptService;
import com.nexa.audit.AuditAction;
import com.nexa.audit.AuditService;
import com.nexa.common.exception.ConflictException;
import com.nexa.common.exception.ResourceNotFoundException;
import com.nexa.common.security.AuthenticatedUser;
import com.nexa.meeting.Meeting;
import com.nexa.meeting.MeetingPermissions;
import com.nexa.meeting.MeetingRepository;
import com.nexa.meeting.MeetingStatus;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class AnalysisService {

    private final MeetingRepository meetingRepository;
    private final MeetingAnalysisRepository analysisRepository;
    private final AuditService auditService;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public AnalysisService(MeetingRepository meetingRepository, MeetingAnalysisRepository analysisRepository,
                           AuditService auditService, ApplicationEventPublisher events, Clock clock) {
        this.meetingRepository = meetingRepository;
        this.analysisRepository = analysisRepository;
        this.auditService = auditService;
        this.events = events;
        this.clock = clock;
    }

    /**
     * Queues an analysis and returns immediately (HTTP 202). The work runs in the background
     * once this transaction commits.
     */
    @Transactional
    public AnalysisResponse request(AuthenticatedUser current, UUID meetingId) {
        Meeting meeting = meetingRepository.findForUpdate(meetingId, current.organizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Meeting", meetingId));
        if (!MeetingPermissions.canEdit(current, meeting)) {
            throw new AccessDeniedException("Not allowed to analyze this meeting");
        }
        if (meeting.getStatus() == MeetingStatus.PROCESSING) {
            throw new ConflictException("This meeting is already being analyzed");
        }
        Instant now = clock.instant();
        MeetingAnalysis analysis = analysisRepository.save(MeetingAnalysis.queue(
                current.organizationId(), meeting.getId(), current.userId(), PromptService.EXTRACTION_VERSION, now));
        meeting.markProcessing(now);
        auditService.record(current.organizationId(), current.userId(), AuditAction.MEETING_ANALYSIS_REQUESTED,
                "MEETING", meeting.getId(), Map.of("analysisId", analysis.getId().toString()));
        events.publishEvent(new MeetingAnalysisRequested(analysis.getId()));
        return AnalysisResponse.from(analysis);
    }

    @Transactional(readOnly = true)
    public AnalysisResponse latest(AuthenticatedUser current, UUID meetingId) {
        meetingRepository.findByIdAndOrganizationId(meetingId, current.organizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Meeting", meetingId));
        return analysisRepository.findFirstByMeetingIdOrderByCreatedAtDesc(meetingId)
                .map(AnalysisResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Analysis for meeting", meetingId));
    }
}
