package com.nexa.action;

import com.nexa.audit.AuditAction;
import com.nexa.audit.AuditService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * State transitions made by the executor, each in its own transaction (REQUIRES_NEW, since the
 * executor may run right after the approving transaction commits on the same thread).
 */
@Component
class ActionStateWriter {

    static final int MAX_ATTEMPTS = 3;
    private static final Duration BASE_BACKOFF = Duration.ofSeconds(30);

    private final AiActionRepository actions;
    private final ExternalActionRepository externals;
    private final AuditService auditService;
    private final Clock clock;

    ActionStateWriter(AiActionRepository actions, ExternalActionRepository externals, AuditService auditService, Clock clock) {
        this.actions = actions;
        this.externals = externals;
        this.auditService = auditService;
        this.clock = clock;
    }

    /** Returns the action if this caller won the right to execute it. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<AiAction> claim(UUID actionId) {
        if (actions.claim(actionId, clock.instant()) == 0) {
            return Optional.empty();
        }
        return actions.findById(actionId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(UUID actionId, ActionOutcome outcome) {
        AiAction action = actions.findById(actionId).orElse(null);
        if (action == null) {
            return;
        }
        Instant now = clock.instant();
        action.complete(outcome.result(), now);
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("actionType", action.getActionType().name());
        metadata.put("attempts", action.getAttempts());
        if (outcome.externalId() != null) {
            externals.save(ExternalAction.record(actionId, outcome.provider(), outcome.externalId(), outcome.externalUrl(), now));
            metadata.put("provider", outcome.provider().name());
            metadata.put("externalId", outcome.externalId());
        }
        auditService.record(action.getOrganizationId(), action.getApprovedBy(), AuditAction.ACTION_COMPLETED, "AI_ACTION", actionId, metadata);
    }

    /** Retries automatically with exponential backoff (30s, 60s) while attempts remain; otherwise fails. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(UUID actionId, String code, String message, boolean retryable) {
        AiAction action = actions.findById(actionId).orElse(null);
        if (action == null) {
            return;
        }
        Instant now = clock.instant();
        if (retryable && action.getAttempts() < MAX_ATTEMPTS) {
            Duration delay = BASE_BACKOFF.multipliedBy(1L << (action.getAttempts() - 1));
            action.scheduleRetry(code, message, now.plus(delay), now);
            return;
        }
        action.fail(code, message, now);
        auditService.record(action.getOrganizationId(), action.getApprovedBy(), AuditAction.ACTION_FAILED, "AI_ACTION", actionId,
                Map.of("actionType", action.getActionType().name(), "errorCode", code, "attempts", action.getAttempts()));
    }
}
