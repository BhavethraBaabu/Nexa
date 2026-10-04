package com.nexa.meeting;

import com.nexa.ai.AiProperties;
import com.nexa.ai.analysis.AnalysisResponse;
import com.nexa.ai.analysis.MeetingAnalysisRepository;
import com.nexa.audit.AuditAction;
import com.nexa.audit.AuditService;
import com.nexa.common.api.PageResponse;
import com.nexa.common.exception.ConflictException;
import com.nexa.common.exception.InvalidRequestException;
import com.nexa.common.exception.ResourceNotFoundException;
import com.nexa.common.security.AuthenticatedUser;
import com.nexa.decision.DecisionRepository;
import com.nexa.meeting.dto.ConfidenceLevel;
import com.nexa.meeting.dto.CreateMeetingRequest;
import com.nexa.meeting.dto.MeetingDetailResponse;
import com.nexa.meeting.dto.MeetingSummaryResponse;
import com.nexa.meeting.dto.UpdateMeetingRequest;
import com.nexa.risk.RiskRepository;
import com.nexa.task.TaskRepository;
import com.nexa.user.MemberNameResolver;
import com.nexa.user.User;
import com.nexa.user.UserRepository;
import com.nexa.user.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Meeting CRUD (PRD section 8). Every query is scoped to the caller's organization. */
@Service
public class MeetingService {

    private static final int MAX_PAGE_SIZE = 100;

    private final MeetingRepository meetingRepository;
    private final MeetingParticipantRepository participantRepository;
    private final MeetingAnalysisRepository analysisRepository;
    private final TaskRepository taskRepository;
    private final DecisionRepository decisionRepository;
    private final RiskRepository riskRepository;
    private final MeetingQuestionRepository questionRepository;
    private final UserRepository userRepository;
    private final MemberNameResolver memberNameResolver;
    private final AuditService auditService;
    private final AiProperties aiProperties;
    private final Clock clock;

    public MeetingService(MeetingRepository meetingRepository, MeetingParticipantRepository participantRepository,
                          MeetingAnalysisRepository analysisRepository, TaskRepository taskRepository,
                          DecisionRepository decisionRepository, RiskRepository riskRepository,
                          MeetingQuestionRepository questionRepository, UserRepository userRepository,
                          MemberNameResolver memberNameResolver, AuditService auditService, AiProperties aiProperties,
                          Clock clock) {
        this.meetingRepository = meetingRepository;
        this.participantRepository = participantRepository;
        this.analysisRepository = analysisRepository;
        this.taskRepository = taskRepository;
        this.decisionRepository = decisionRepository;
        this.riskRepository = riskRepository;
        this.questionRepository = questionRepository;
        this.userRepository = userRepository;
        this.memberNameResolver = memberNameResolver;
        this.auditService = auditService;
        this.aiProperties = aiProperties;
        this.clock = clock;
    }

    @Transactional
    public MeetingDetailResponse create(AuthenticatedUser current, CreateMeetingRequest request) {
        String transcript = checkTranscript(request.transcript());
        Meeting meeting = meetingRepository.save(Meeting.create(current.organizationId(), request.title().trim(),
                request.meetingDate(), request.durationMinutes(), transcript, current.userId(), clock.instant()));
        replaceParticipants(meeting, request.participants());
        auditService.record(current.organizationId(), current.userId(), AuditAction.MEETING_CREATED, "MEETING",
                meeting.getId(), Map.of("title", meeting.getTitle()));
        return toDetail(current, meeting);
    }

    @Transactional(readOnly = true)
    public PageResponse<MeetingSummaryResponse> list(AuthenticatedUser current, int page, int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE),
                Sort.by(Sort.Order.desc("meetingDate"), Sort.Order.desc("createdAt")));
        Page<Meeting> meetings = meetingRepository.findByOrganizationId(current.organizationId(), pageable);
        Map<UUID, Long> counts = participantCounts(meetings.map(Meeting::getId).getContent());
        return PageResponse.from(meetings, m -> MeetingSummaryResponse.from(m, counts.getOrDefault(m.getId(), 0L)));
    }

    @Transactional(readOnly = true)
    public MeetingDetailResponse get(AuthenticatedUser current, UUID meetingId) {
        return toDetail(current, load(current, meetingId));
    }

    @Transactional
    public MeetingDetailResponse update(AuthenticatedUser current, UUID meetingId, UpdateMeetingRequest request) {
        Meeting meeting = loadEditable(current, meetingId);
        Instant now = clock.instant();
        meeting.updateDetails(
                request.title() != null ? request.title().trim() : meeting.getTitle(),
                request.meetingDate() != null ? request.meetingDate() : meeting.getMeetingDate(),
                request.durationMinutes() != null ? request.durationMinutes() : meeting.getDurationMinutes(),
                now);
        if (request.participants() != null) {
            replaceParticipants(meeting, request.participants());
        }
        boolean transcriptChanged = request.transcript() != null
                && !request.transcript().strip().equals(meeting.getTranscript());
        if (transcriptChanged) {
            if (meeting.getStatus() == MeetingStatus.PROCESSING) {
                throw new ConflictException("The transcript can't be changed while the meeting is being analyzed");
            }
            meeting.replaceTranscript(checkTranscript(request.transcript()), now);
            clearAiResults(meeting.getId());
        }
        auditService.record(current.organizationId(), current.userId(), AuditAction.MEETING_UPDATED, "MEETING",
                meeting.getId(), Map.of("transcriptChanged", transcriptChanged));
        return toDetail(current, meeting);
    }

    @Transactional
    public void delete(AuthenticatedUser current, UUID meetingId) {
        Meeting meeting = loadEditable(current, meetingId);
        auditService.record(current.organizationId(), current.userId(), AuditAction.MEETING_DELETED, "MEETING",
                meeting.getId(), Map.of("title", meeting.getTitle()));
        meetingRepository.delete(meeting);
    }

    private Meeting load(AuthenticatedUser current, UUID meetingId) {
        return meetingRepository.findByIdAndOrganizationId(meetingId, current.organizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Meeting", meetingId));
    }

    private Meeting loadEditable(AuthenticatedUser current, UUID meetingId) {
        Meeting meeting = load(current, meetingId);
        if (!MeetingPermissions.canEdit(current, meeting)) {
            throw new AccessDeniedException("Not allowed to change this meeting");
        }
        return meeting;
    }

    private String checkTranscript(String transcript) {
        String stripped = transcript.strip();
        if (stripped.length() > aiProperties.maxTranscriptChars()) {
            throw new InvalidRequestException("Transcript is too long (%,d characters). The limit is %,d; split long meetings into parts."
                    .formatted(stripped.length(), aiProperties.maxTranscriptChars()));
        }
        return stripped;
    }

    private void replaceParticipants(Meeting meeting, List<String> names) {
        participantRepository.deleteByMeetingId(meeting.getId());
        if (names == null || names.isEmpty()) {
            return;
        }
        List<User> members = userRepository.findByOrganizationIdAndStatusOrderByNameAsc(meeting.getOrganizationId(), UserStatus.ACTIVE);
        int position = 0;
        for (String name : new LinkedHashSet<>(names.stream().map(String::trim).filter(n -> !n.isEmpty()).toList())) {
            UUID userId = memberNameResolver.resolveUnique(name, members).map(User::getId).orElse(null);
            participantRepository.save(MeetingParticipant.create(meeting.getId(), name, userId, position++));
        }
    }

    private void clearAiResults(UUID meetingId) {
        taskRepository.deleteSuggestedForMeeting(meetingId);
        decisionRepository.deleteByMeetingId(meetingId);
        riskRepository.deleteByMeetingId(meetingId);
        questionRepository.deleteByMeetingId(meetingId);
    }

    private Map<UUID, Long> participantCounts(Collection<UUID> meetingIds) {
        if (meetingIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Long> counts = new HashMap<>();
        for (Object[] row : participantRepository.countByMeetingIds(meetingIds)) {
            counts.put((UUID) row[0], (Long) row[1]);
        }
        return counts;
    }

    private MeetingDetailResponse toDetail(AuthenticatedUser current, Meeting meeting) {
        List<MeetingParticipant> participants = participantRepository.findByMeetingIdOrderByPositionAsc(meeting.getId());
        var tasks = taskRepository.findByMeetingIdOrderByPositionAsc(meeting.getId());

        List<UUID> userIds = new ArrayList<>();
        userIds.add(meeting.getCreatedBy());
        tasks.stream().map(t -> t.getOwnerId()).filter(Objects::nonNull).forEach(userIds::add);
        Map<UUID, User> users = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        Function<UUID, MeetingDetailResponse.Person> person = id -> {
            User user = id == null ? null : users.get(id);
            return user == null ? null : new MeetingDetailResponse.Person(user.getId(), user.getName());
        };

        AnalysisResponse latestRun = analysisRepository.findFirstByMeetingIdOrderByCreatedAtDesc(meeting.getId())
                .map(AnalysisResponse::from)
                .orElse(null);
        MeetingDetailResponse.Analysis analysis = null;
        if (latestRun != null) {
            analysis = new MeetingDetailResponse.Analysis(
                    latestRun,
                    meeting.getSummary(),
                    meeting.getKeyPoints(),
                    meeting.getAnalyzedAt(),
                    tasks.stream().map(t -> new MeetingDetailResponse.ActionItem(t.getId(), t.getTitle(), t.getDescription(),
                            person.apply(t.getOwnerId()), t.getOwnerName(), t.getOwnerStatus(), t.getPriority(), t.getStatus(),
                            t.getDeadline(), t.getDeadlineText(), t.getDeadlineStatus(), t.getAiConfidence(),
                            ConfidenceLevel.of(t.getAiConfidence()), t.getEvidence())).toList(),
                    decisionRepository.findByMeetingIdOrderByPositionAsc(meeting.getId()).stream()
                            .map(d -> new MeetingDetailResponse.DecisionView(d.getId(), d.getDecision(), d.getContext(),
                                    d.getAiConfidence(), ConfidenceLevel.of(d.getAiConfidence()), d.getEvidence())).toList(),
                    riskRepository.findByMeetingIdOrderByPositionAsc(meeting.getId()).stream()
                            .map(r -> new MeetingDetailResponse.RiskView(r.getId(), r.getDescription(), r.getSeverity(),
                                    r.getAiConfidence(), ConfidenceLevel.of(r.getAiConfidence()), r.getEvidence())).toList(),
                    questionRepository.findByMeetingIdOrderByPositionAsc(meeting.getId()).stream()
                            .map(q -> new MeetingDetailResponse.QuestionView(q.getId(), q.getQuestion(), q.getStatus(),
                                    q.getAiConfidence(), ConfidenceLevel.of(q.getAiConfidence()), q.getEvidence())).toList());
        }

        return new MeetingDetailResponse(meeting.getId(), meeting.getTitle(), meeting.getMeetingDate(),
                meeting.getDurationMinutes(), meeting.getStatus(), meeting.getTranscript(),
                participants.stream().map(p -> new MeetingDetailResponse.Participant(p.getName(), p.getUserId())).toList(),
                person.apply(meeting.getCreatedBy()), meeting.getCreatedAt(), meeting.getUpdatedAt(),
                MeetingPermissions.canEdit(current, meeting), analysis);
    }
}
