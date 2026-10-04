package com.nexa.action;

import com.nexa.task.Task;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Proposes actions for freshly analyzed work (PRD section 15): a Jira issue and a Slack
 * notification per action item, and one follow-up email draft per meeting. Every proposal starts
 * PENDING and does nothing until approved.
 */
@Component
public class ActionGenerator {

    private final AiActionRepository repository;

    public ActionGenerator(AiActionRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void replaceSuggestions(UUID organizationId, UUID meetingId, List<Task> newTasks, UUID requestedBy, Instant now) {
        repository.deletePendingForMeeting(meetingId);
        for (Task task : newTasks) {
            for (ActionType type : ActionType.values()) {
                if (type.perTask()) {
                    repository.save(AiAction.suggest(organizationId, meetingId, task.getId(), type, requestedBy, now));
                }
            }
        }
        // A completed draft from an earlier analysis is kept; only suggest one if none exists.
        if (!repository.existsByIdempotencyKey(AiAction.idempotencyKey(meetingId, null, ActionType.DRAFT_EMAIL))) {
            repository.save(AiAction.suggest(organizationId, meetingId, null, ActionType.DRAFT_EMAIL, requestedBy, now));
        }
    }
}
