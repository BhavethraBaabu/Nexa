package com.nexa.ai.analysis;

import java.util.UUID;

/**
 * Published when a user asks for an analysis (PRD section 36). Delivered in-process after the
 * request's transaction commits; the event interface lets Kafka replace this later without
 * changing the producer.
 */
public record MeetingAnalysisRequested(UUID analysisId) {
}
