package com.nexa.task;

import com.nexa.action.ActionStatus;
import com.nexa.action.AiAction;
import com.nexa.action.AiActionRepository;
import com.nexa.action.ExternalAction;
import com.nexa.action.ExternalActionRepository;
import com.nexa.audit.AuditAction;
import com.nexa.audit.AuditService;
import com.nexa.common.api.PageResponse;
import com.nexa.common.exception.InvalidRequestException;
import com.nexa.common.exception.ResourceNotFoundException;
import com.nexa.common.security.AuthenticatedUser;
import com.nexa.meeting.Meeting;
import com.nexa.meeting.MeetingPermissions;
import com.nexa.meeting.MeetingRepository;
import com.nexa.meeting.dto.ConfidenceLevel;
import com.nexa.task.dto.TaskResponse;
import com.nexa.task.dto.UpdateTaskRequest;
import com.nexa.user.User;
import com.nexa.user.UserRepository;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** The task dashboard and task editing (PRD sections 15 and 26). */
@Service
public class TaskService {

    private final TaskRepository tasks;
    private final MeetingRepository meetings;
    private final UserRepository users;
    private final AiActionRepository actions;
    private final ExternalActionRepository externals;
    private final AuditService auditService;
    private final Clock clock;

    public TaskService(TaskRepository tasks, MeetingRepository meetings, UserRepository users, AiActionRepository actions,
                       ExternalActionRepository externals, AuditService auditService, Clock clock) {
        this.tasks = tasks;
        this.meetings = meetings;
        this.users = users;
        this.actions = actions;
        this.externals = externals;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<TaskResponse> list(AuthenticatedUser current, TaskFilter filter, int page, int size) {
        Sort sort = Sort.by(Sort.Order.asc("deadline").nullsLast(), Sort.Order.desc("createdAt"), Sort.Order.asc("position"));
        Page<Task> result = tasks.findAll(spec(current, filter), PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 100), sort));
        return new PageResponse<>(toResponses(current, result.getContent()), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public TaskResponse get(AuthenticatedUser current, UUID id) {
        return toResponses(current, List.of(load(current, id))).getFirst();
    }

    @Transactional
    public TaskResponse update(AuthenticatedUser current, UUID id, UpdateTaskRequest request) {
        Task task = load(current, id);
        Meeting meeting = meetings.findById(task.getMeetingId()).orElseThrow();
        boolean fullEdit = MeetingPermissions.canEdit(current, meeting);
        boolean ownerOnlyStatus = !fullEdit && current.userId().equals(task.getOwnerId());
        if (!fullEdit && !ownerOnlyStatus) {
            throw new AccessDeniedException("Not allowed to edit this task");
        }
        if (ownerOnlyStatus && (request.title() != null || request.description() != null || request.ownerId() != null
                || Boolean.TRUE.equals(request.clearOwner()) || request.priority() != null || request.deadline() != null
                || Boolean.TRUE.equals(request.clearDeadline()))) {
            throw new AccessDeniedException("Task owners can only change the status of their own tasks");
        }

        boolean ownerChanged = request.ownerId() != null || Boolean.TRUE.equals(request.clearOwner());
        User owner = null;
        if (request.ownerId() != null) {
            // Owners must be active members of the same organization: never an arbitrary user ID.
            owner = users.findByIdAndOrganizationId(request.ownerId(), current.organizationId())
                    .filter(User::isActive)
                    .orElseThrow(() -> new InvalidRequestException("The selected owner isn't a member of your organization"));
        }
        boolean deadlineChanged = request.deadline() != null || Boolean.TRUE.equals(request.clearDeadline());

        Map<String, Object> changes = new LinkedHashMap<>();
        if (request.title() != null) changes.put("title", request.title().strip());
        if (request.priority() != null) changes.put("priority", request.priority().name());
        if (request.status() != null) changes.put("status", request.status().name());
        if (ownerChanged) changes.put("owner", owner == null ? "none" : owner.getName());
        if (deadlineChanged) changes.put("deadline", request.deadline() == null ? "none" : request.deadline().toString());
        if (request.description() != null) changes.put("description", "changed");

        task.edit(new TaskEdit(request.title() == null ? null : request.title().strip(), request.description(), ownerChanged,
                owner == null ? null : owner.getId(), owner == null ? null : owner.getName(), request.priority(),
                deadlineChanged, request.deadline(), request.status()), current.userId(), clock.instant());
        auditService.record(current.organizationId(), current.userId(), AuditAction.TASK_UPDATED, "TASK", task.getId(), changes);
        return toResponses(current, List.of(task)).getFirst();
    }

    private Task load(AuthenticatedUser current, UUID id) {
        return tasks.findByIdAndOrganizationId(id, current.organizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Task", id));
    }

    private Specification<Task> spec(AuthenticatedUser current, TaskFilter filter) {
        LocalDate today = LocalDate.now(clock);
        return (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();
            where.add(cb.equal(root.get("organizationId"), current.organizationId()));
            var status = root.<TaskStatus>get("status");
            switch (filter) {
                case ALL -> where.add(cb.notEqual(status, TaskStatus.CANCELLED));
                case MINE -> {
                    where.add(cb.equal(root.get("ownerId"), current.userId()));
                    where.add(cb.notEqual(status, TaskStatus.CANCELLED));
                }
                case TEAM -> {
                    where.add(cb.isNotNull(root.get("ownerId")));
                    where.add(cb.notEqual(root.get("ownerId"), current.userId()));
                    where.add(cb.notEqual(status, TaskStatus.CANCELLED));
                }
                case OVERDUE -> {
                    where.add(cb.lessThan(root.get("deadline"), today));
                    where.add(status.in(TaskStatus.SUGGESTED, TaskStatus.OPEN, TaskStatus.IN_PROGRESS));
                }
                case COMPLETED -> where.add(cb.equal(status, TaskStatus.DONE));
                case SUGGESTED -> where.add(cb.equal(status, TaskStatus.SUGGESTED));
                case PENDING_APPROVAL -> {
                    Subquery<UUID> pending = query.subquery(UUID.class);
                    var action = pending.from(AiAction.class);
                    pending.select(action.get("taskId")).where(cb.equal(action.get("status"), ActionStatus.PENDING),
                            cb.equal(action.get("organizationId"), current.organizationId()));
                    where.add(root.get("id").in(pending));
                }
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
    }

    private List<TaskResponse> toResponses(AuthenticatedUser current, List<Task> list) {
        if (list.isEmpty()) {
            return List.of();
        }
        LocalDate today = LocalDate.now(clock);
        Map<UUID, Meeting> meetingsById = meetings.findAllById(list.stream().map(Task::getMeetingId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Meeting::getId, Function.identity()));
        Set<UUID> userIds = new HashSet<>();
        list.forEach(t -> {
            if (t.getOwnerId() != null) userIds.add(t.getOwnerId());
            if (t.getEditedBy() != null) userIds.add(t.getEditedBy());
        });
        Map<UUID, User> people = users.findAllById(userIds).stream().collect(Collectors.toMap(User::getId, Function.identity()));
        List<AiAction> taskActions = actions.findByTaskIdIn(list.stream().map(Task::getId).toList());
        Map<UUID, ExternalAction> externalByAction = externals.findByAiActionIdIn(taskActions.stream().map(AiAction::getId).toList())
                .stream().collect(Collectors.toMap(ExternalAction::getAiActionId, Function.identity()));
        Map<UUID, List<AiAction>> actionsByTask = taskActions.stream().collect(Collectors.groupingBy(AiAction::getTaskId));

        return list.stream().map(t -> {
            Meeting m = meetingsById.get(t.getMeetingId());
            List<AiAction> own = actionsByTask.getOrDefault(t.getId(), List.of());
            List<TaskResponse.ExternalLink> links = own.stream().map(a -> externalByAction.get(a.getId())).filter(Objects::nonNull)
                    .map(e -> new TaskResponse.ExternalLink(e.getProvider(), e.getExternalId(), e.getExternalUrl())).toList();
            int pending = (int) own.stream().filter(a -> a.getStatus() == ActionStatus.PENDING).count();
            boolean canEdit = m != null && (MeetingPermissions.canEdit(current, m) || current.userId().equals(t.getOwnerId()));
            return new TaskResponse(t.getId(), t.getTitle(), t.getDescription(), person(people.get(t.getOwnerId())), t.getOwnerName(),
                    t.getOwnerStatus(), t.getPriority(), t.getStatus(), t.getDeadline(), t.getDeadlineText(), t.getDeadlineStatus(),
                    t.isOverdue(today), t.getAiConfidence(), ConfidenceLevel.of(t.getAiConfidence()), t.getEvidence(),
                    m == null ? null : new TaskResponse.MeetingRef(m.getId(), m.getTitle(), m.getMeetingDate()), pending, links,
                    canEdit, person(people.get(t.getEditedBy())), t.getEditedAt(), t.getCreatedAt());
        }).toList();
    }

    private static TaskResponse.Person person(User user) {
        return user == null ? null : new TaskResponse.Person(user.getId(), user.getName());
    }
}
