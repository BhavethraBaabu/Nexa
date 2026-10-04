package com.nexa.integration;

import java.time.Instant;

/** What the API shows about an integration. Never includes tokens (PRD section 45). */
public record IntegrationResponse(IntegrationProvider provider, boolean available, boolean connected,
                                  IntegrationStatus status, String workspaceName, boolean configured,
                                  Object config, Instant connectedAt) {
}
