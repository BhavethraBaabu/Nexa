package com.nexa.ai.analysis;

import com.nexa.ai.AiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.List;

/**
 * Fails analyses that will never finish, for example because the server restarted mid-job or the
 * work queue was full. Without this a meeting could show "Analyzing..." forever and could never
 * be retried.
 */
@Component
class AnalysisRecovery {

    private static final Logger log = LoggerFactory.getLogger(AnalysisRecovery.class);

    private final MeetingAnalysisRepository repository;
    private final AnalysisResultWriter writer;
    private final AiProperties properties;
    private final Clock clock;

    AnalysisRecovery(MeetingAnalysisRepository repository, AnalysisResultWriter writer, AiProperties properties, Clock clock) {
        this.repository = repository;
        this.writer = writer;
        this.properties = properties;
        this.clock = clock;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Scheduled(fixedDelayString = "PT5M", initialDelayString = "PT5M")
    void failStaleAnalyses() {
        List<MeetingAnalysis> stale = repository.findStale(
                List.of(AnalysisStatus.QUEUED, AnalysisStatus.PROCESSING), clock.instant().minus(properties.staleAfter()));
        for (MeetingAnalysis analysis : stale) {
            writer.fail(analysis.getId(), "TIMED_OUT", "The analysis didn't finish. Your transcript is safe; please try again.", null);
        }
        if (!stale.isEmpty()) {
            log.warn("Marked {} stale analyses as failed", stale.size());
        }
    }
}
