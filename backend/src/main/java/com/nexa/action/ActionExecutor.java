package com.nexa.action;

import com.nexa.decision.Decision;
import com.nexa.decision.DecisionRepository;
import com.nexa.integration.IntegrationException;
import com.nexa.meeting.Meeting;
import com.nexa.meeting.MeetingRepository;
import com.nexa.task.Task;
import com.nexa.task.TaskRepository;
import com.nexa.task.TaskStatus;
import com.nexa.user.User;
import com.nexa.user.UserRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Runs approved actions (PRD sections 16, 22, 51). The external call happens outside any database
 * transaction. Approved actions are durable: a failure is recorded and retried, never lost.
 */
@Component
public class ActionExecutor {

    private static final Logger log = LoggerFactory.getLogger(ActionExecutor.class);

    private final ActionStateWriter writer;
    private final Map<ActionType, ActionHandler> handlers;
    private final MeetingRepository meetings;
    private final TaskRepository tasks;
    private final DecisionRepository decisions;
    private final UserRepository users;
    private final TransactionTemplate readTx;
    private final MeterRegistry meters;

    public ActionExecutor(ActionStateWriter writer, List<ActionHandler> handlers, MeetingRepository meetings, TaskRepository tasks,
                          DecisionRepository decisions, UserRepository users, TransactionTemplate transactionTemplate,
                          MeterRegistry meters) {
        this.writer = writer;
        this.handlers = handlers.stream().collect(Collectors.toMap(ActionHandler::type, Function.identity()));
        this.meetings = meetings;
        this.tasks = tasks;
        this.decisions = decisions;
        this.users = users;
        this.readTx = new TransactionTemplate(transactionTemplate.getTransactionManager());
        this.readTx.setReadOnly(true);
        this.readTx.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);
        this.meters = meters;
    }

    @Async("actionTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onExecutionRequested(ActionExecutionRequested event) {
        run(event.actionId());
    }

    /** Entry point for the retry scheduler. */
    @Async("actionTaskExecutor")
    public void dispatch(UUID actionId) {
        run(actionId);
    }

    void run(UUID actionId) {
        AiAction action = writer.claim(actionId).orElse(null);
        if (action == null) {
            return; // Someone else is running it, or it's no longer approved.
        }
        MDC.put("actionId", actionId.toString());
        try {
            ActionHandler handler = handlers.get(action.getActionType());
            ActionContext context = readTx.execute(status -> context(action));
            if (context == null) {
                writer.recordFailure(actionId, "SOURCE_DELETED", "The meeting or task behind this action was deleted.", false);
                return;
            }
            ActionOutcome outcome = handler.execute(context);
            writer.complete(actionId, outcome);
            meters.counter("nexa.actions.executions", "type", action.getActionType().name(), "outcome", "success").increment();
            log.info("Executed {} (attempt {})", action.getActionType(), action.getAttempts());
        } catch (IntegrationException e) {
            log.warn("{} failed with {} (attempt {})", action.getActionType(), e.code(), action.getAttempts());
            writer.recordFailure(actionId, e.code().name(), e.userMessage(), e.retryable());
            meters.counter("nexa.actions.executions", "type", action.getActionType().name(), "outcome", e.code().name()).increment();
        } catch (RuntimeException e) {
            log.error("{} failed unexpectedly (attempt {})", action.getActionType(), action.getAttempts(), e);
            writer.recordFailure(actionId, "FAILED", "Something went wrong running this action. Nexa will retry.", true);
            meters.counter("nexa.actions.executions", "type", action.getActionType().name(), "outcome", "ERROR").increment();
        } finally {
            MDC.remove("actionId");
        }
    }

    private ActionContext context(AiAction action) {
        Meeting meeting = meetings.findByIdAndOrganizationId(action.getMeetingId(), action.getOrganizationId()).orElse(null);
        if (meeting == null) {
            return null;
        }
        List<Task> meetingTasks = tasks.findByMeetingIdOrderByPositionAsc(meeting.getId()).stream()
                .filter(t -> t.getStatus() != TaskStatus.CANCELLED)
                .toList();
        Task task = null;
        if (action.getTaskId() != null) {
            task = tasks.findById(action.getTaskId()).orElse(null);
            if (task == null) {
                return null;
            }
        }
        List<UUID> ownerIds = meetingTasks.stream().map(Task::getOwnerId).filter(Objects::nonNull).distinct().toList();
        Map<UUID, User> people = users.findAllById(ownerIds).stream().collect(Collectors.toMap(User::getId, Function.identity()));
        if (task != null && task.getOwnerId() != null && !people.containsKey(task.getOwnerId())) {
            users.findById(task.getOwnerId()).ifPresent(u -> people.put(u.getId(), u));
        }
        List<Decision> meetingDecisions = decisions.findByMeetingIdOrderByPositionAsc(meeting.getId());
        User owner = task == null || task.getOwnerId() == null ? null : people.get(task.getOwnerId());
        return new ActionContext(action, meeting, task, owner, meetingTasks, meetingDecisions, people);
    }
}
