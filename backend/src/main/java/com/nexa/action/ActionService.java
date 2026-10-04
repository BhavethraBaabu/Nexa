package com.nexa.action;

import com.nexa.audit.AuditAction;
import com.nexa.audit.AuditService;
import com.nexa.common.api.PageResponse;
import com.nexa.common.exception.BusinessRuleException;
import com.nexa.common.exception.InvalidRequestException;
import com.nexa.common.exception.ResourceNotFoundException;
import com.nexa.common.security.AuthenticatedUser;
import com.nexa.integration.IntegrationProvider;
import com.nexa.integration.IntegrationService;
import com.nexa.meeting.Meeting;
import com.nexa.meeting.MeetingPermissions;
import com.nexa.meeting.MeetingRepository;
import com.nexa.task.Task;
import com.nexa.task.TaskRepository;
import com.nexa.user.User;
import com.nexa.user.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Human review of AI-proposed actions (PRD sections 15, 16, 23). Every write action needs explicit
 * approval; approval and rejection are audited.
 */
@Service
public class ActionService {

    private static final int MAX_BATCH = 100;
    private static final Set<ActionStatus> HISTORY = Set.of(ActionStatus.APPROVED, ActionStatus.EXECUTING,
            ActionStatus.COMPLETED, ActionStatus.FAILED, ActionStatus.REJECTED, ActionStatus.CANCELLED);

    private final AiActionRepository actions;
    private final ExternalActionRepository externals;
    private final MeetingRepository meetings;
    private final TaskRepository tasks;
    private final UserRepository users;
    private final IntegrationService integrations;
    private final AuditService auditService;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public ActionService(AiActionRepository actions, ExternalActionRepository externals, MeetingRepository meetings,
                         TaskRepository tasks, UserRepository users, IntegrationService integrations, AuditService auditService,
                         ApplicationEventPublisher events, Clock clock) {
        this.actions = actions;
        this.externals = externals;
        this.meetings = meetings;
        this.tasks = tasks;
        this.users = users;
        this.integrations = integrations;
        this.auditService = auditService;
        this.events = events;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<ActionResponse> list(AuthenticatedUser current, boolean pending, int page, int size) {
        Page<AiAction> result = actions.findByOrganizationIdAndStatusIn(current.organizationId(),
                pending ? Set.of(ActionStatus.PENDING) : HISTORY,
                PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 100),
                        pending ? Sort.by("createdAt", "id") : Sort.by(Sort.Order.desc("updatedAt"))));
        List<ActionResponse> content = toResponses(current, result.getContent());
        return new PageResponse<>(content, result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public List<ActionResponse> forMeeting(AuthenticatedUser current, UUID meetingId) {
        meetings.findByIdAndOrganizationId(meetingId, current.organizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Meeting", meetingId));
        return toResponses(current, actions.findByMeetingIdOrderByCreatedAtAsc(meetingId));
    }

    /**
     * Approves actions and starts executing them. Approving an already-approved action is a no-op,
     * so a double click can never create two Jira issues (PRD section 52).
     */
    @Transactional
    public List<ActionResponse> approve(AuthenticatedUser current, Collection<UUID> ids) {
        List<AiAction> selected = loadBatch(current, ids);
        Map<UUID, Meeting> meetingsById = meetingsFor(selected);
        Map<IntegrationProvider, String> blocked = new EnumMap<>(IntegrationProvider.class);
        Instant now = clock.instant();

        for (AiAction action : selected) {
            requireApprover(current, meetingsById.get(action.getMeetingId()));
            if (action.getStatus() != ActionStatus.PENDING) {
                if (action.getStatus() == ActionStatus.REJECTED || action.getStatus() == ActionStatus.CANCELLED) {
                    throw new BusinessRuleException("This action was already " + action.getStatus().name().toLowerCase() + " and can't be approved.");
                }
                continue;
            }
            IntegrationProvider provider = action.getActionType().provider();
            if (provider != null) {
                String reason = blocked.computeIfAbsent(provider, p -> integrations.blockedReason(current.organizationId(), p));
                if (reason != null) {
                    throw new BusinessRuleException(reason);
                }
            }
        }
        for (AiAction action : selected) {
            if (action.getStatus() != ActionStatus.PENDING) {
                continue;
            }
            action.approve(current.userId(), now);
            if (action.getTaskId() != null) {
                tasks.findById(action.getTaskId()).ifPresent(t -> t.acceptIfSuggested(now));
            }
            auditService.record(current.organizationId(), current.userId(), AuditAction.ACTION_APPROVED, "AI_ACTION", action.getId(),
                    Map.of("actionType", action.getActionType().name()));
            events.publishEvent(new ActionExecutionRequested(action.getId()));
        }
        return toResponses(current, selected);
    }

    @Transactional
    public List<ActionResponse> reject(AuthenticatedUser current, Collection<UUID> ids) {
        List<AiAction> selected = loadBatch(current, ids);
        Map<UUID, Meeting> meetingsById = meetingsFor(selected);
        Instant now = clock.instant();
        for (AiAction action : selected) {
            requireApprover(current, meetingsById.get(action.getMeetingId()));
            if (action.getStatus() == ActionStatus.REJECTED) {
                continue;
            }
            if (action.getStatus() != ActionStatus.PENDING) {
                throw new BusinessRuleException("Only pending actions can be rejected.");
            }
            action.reject(current.userId(), now);
            auditService.record(current.organizationId(), current.userId(), AuditAction.ACTION_REJECTED, "AI_ACTION", action.getId(),
                    Map.of("actionType", action.getActionType().name()));
        }
        return toResponses(current, selected);
    }

    /** Runs a failed action again (PRD section 51: FAILED → RETRY). */
    @Transactional
    public ActionResponse retry(AuthenticatedUser current, UUID id) {
        AiAction action = load(current, id);
        requireApprover(current, meetingsFor(List.of(action)).get(action.getMeetingId()));
        if (action.getStatus() != ActionStatus.FAILED) {
            throw new BusinessRuleException("Only failed actions can be retried.");
        }
        IntegrationProvider provider = action.getActionType().provider();
        if (provider != null) {
            String reason = integrations.blockedReason(current.organizationId(), provider);
            if (reason != null) {
                throw new BusinessRuleException(reason);
            }
        }
        action.manualRetry(clock.instant());
        auditService.record(current.organizationId(), current.userId(), AuditAction.ACTION_RETRIED, "AI_ACTION", id,
                Map.of("actionType", action.getActionType().name()));
        events.publishEvent(new ActionExecutionRequested(id));
        return toResponses(current, List.of(action)).getFirst();
    }

    /** Runs an approved action now instead of waiting for its scheduled retry. */
    @Transactional
    public ActionResponse execute(AuthenticatedUser current, UUID id) {
        AiAction action = load(current, id);
        requireApprover(current, meetingsFor(List.of(action)).get(action.getMeetingId()));
        if (action.getStatus() != ActionStatus.APPROVED) {
            throw new BusinessRuleException("Only approved actions waiting to run can be executed.");
        }
        events.publishEvent(new ActionExecutionRequested(id));
        return toResponses(current, List.of(action)).getFirst();
    }

    @Transactional(readOnly = true)
    public long pendingCount(AuthenticatedUser current) {
        return actions.countByOrganizationIdAndStatus(current.organizationId(), ActionStatus.PENDING);
    }

    private AiAction load(AuthenticatedUser current, UUID id) {
        return actions.findByIdAndOrganizationId(id, current.organizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Action", id));
    }

    private List<AiAction> loadBatch(AuthenticatedUser current, Collection<UUID> ids) {
        Set<UUID> unique = new LinkedHashSet<>(ids);
        if (unique.isEmpty() || unique.size() > MAX_BATCH) {
            throw new InvalidRequestException("Select between 1 and " + MAX_BATCH + " actions");
        }
        List<AiAction> found = actions.findByIdInAndOrganizationId(unique, current.organizationId());
        if (found.size() != unique.size()) {
            // Includes IDs from other organizations: reveal nothing about them.
            throw new ResourceNotFoundException("Action", "one or more of the selected actions");
        }
        return found;
    }

    private Map<UUID, Meeting> meetingsFor(List<AiAction> list) {
        Set<UUID> ids = list.stream().map(AiAction::getMeetingId).collect(Collectors.toSet());
        return meetings.findAllById(ids).stream().collect(Collectors.toMap(Meeting::getId, Function.identity()));
    }

    /** Managers and admins approve anything; members approve actions from meetings they created (PRD section 7.3). */
    private static void requireApprover(AuthenticatedUser current, Meeting meeting) {
        if (meeting == null || !MeetingPermissions.canEdit(current, meeting)) {
            throw new AccessDeniedException("Not allowed to approve actions for this meeting");
        }
    }

    private List<ActionResponse> toResponses(AuthenticatedUser current, List<AiAction> list) {
        if (list.isEmpty()) {
            return List.of();
        }
        Map<UUID, Meeting> meetingsById = meetingsFor(list);
        Map<UUID, Task> tasksById = tasks.findAllById(list.stream().map(AiAction::getTaskId).filter(Objects::nonNull).toList())
                .stream().collect(Collectors.toMap(Task::getId, Function.identity()));
        Set<UUID> userIds = new HashSet<>();
        list.forEach(a -> {
            userIds.add(a.getRequestedBy());
            if (a.getApprovedBy() != null) {
                userIds.add(a.getApprovedBy());
            }
        });
        tasksById.values().stream().map(Task::getOwnerId).filter(Objects::nonNull).forEach(userIds::add);
        Map<UUID, User> people = users.findAllById(userIds).stream().collect(Collectors.toMap(User::getId, Function.identity()));
        Map<UUID, ExternalAction> externalByAction = externals.findByAiActionIdIn(list.stream().map(AiAction::getId).toList())
                .stream().collect(Collectors.toMap(ExternalAction::getAiActionId, Function.identity()));
        Map<IntegrationProvider, String> blocked = new HashMap<>();
        Stream.of(IntegrationProvider.JIRA, IntegrationProvider.SLACK)
                .forEach(p -> blocked.put(p, integrations.blockedReason(current.organizationId(), p)));

        return list.stream().map(a -> {
            Meeting m = meetingsById.get(a.getMeetingId());
            Task t = a.getTaskId() == null ? null : tasksById.get(a.getTaskId());
            ExternalAction ext = externalByAction.get(a.getId());
            User owner = t == null || t.getOwnerId() == null ? null : people.get(t.getOwnerId());
            boolean retryable = a.getStatus() == ActionStatus.FAILED && isRetryable(a.getErrorCode());
            IntegrationProvider provider = a.getActionType().provider();
            return new ActionResponse(a.getId(), a.getActionType(), a.getStatus(), provider,
                    m == null ? null : new ActionResponse.MeetingRef(m.getId(), m.getTitle(), m.getMeetingDate()),
                    t == null ? null : new ActionResponse.TaskRef(t.getId(), t.getTitle(),
                            owner != null ? owner.getName() : t.getOwnerName(), owner != null, t.getPriority(), t.getDeadline(),
                            t.getDeadlineStatus(), t.getStatus()),
                    person(people.get(a.getRequestedBy())), person(people.get(a.getApprovedBy())), a.getApprovedAt(),
                    a.getExecutedAt(), a.getAttempts(), a.getNextAttemptAt(), a.getErrorCode(), a.getErrorMessage(), retryable,
                    ext == null ? null : new ActionResponse.External(ext.getProvider(), ext.getExternalId(), ext.getExternalUrl()),
                    a.getResult(), m != null && MeetingPermissions.canEdit(current, m),
                    provider == null ? null : blocked.get(provider), a.getCreatedAt());
        }).toList();
    }

    /**
     * Whether a person may retry a failed action. Almost always: configuration and credential
     * problems are fixable in Integrations. Not when the source meeting or task is gone.
     */
    private static boolean isRetryable(String code) {
        return !"SOURCE_DELETED".equals(code);
    }

    private static ActionResponse.Person person(User user) {
        return user == null ? null : new ActionResponse.Person(user.getId(), user.getName());
    }
}
