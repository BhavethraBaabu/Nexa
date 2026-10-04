package com.nexa.action;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Drives automatic retries and recovers executions interrupted by a restart (PRD section 51).
 * Disabled in tests, which trigger {@link #tick()} directly.
 */
@Component
@ConditionalOnProperty(name = "nexa.actions.scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class ActionScheduler {

    private static final Duration STALE_EXECUTION = Duration.ofMinutes(10);

    private final AiActionRepository repository;
    private final ActionExecutor executor;
    private final Clock clock;

    public ActionScheduler(AiActionRepository repository, ActionExecutor executor, Clock clock) {
        this.repository = repository;
        this.executor = executor;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "PT15S", initialDelayString = "PT15S")
    public void tick() {
        releaseStale();
        for (UUID id : repository.findDue(clock.instant())) {
            executor.dispatch(id);
        }
    }

    private void releaseStale() {
        Instant now = clock.instant();
        repository.releaseStale(now.minus(STALE_EXECUTION), now);
    }
}
