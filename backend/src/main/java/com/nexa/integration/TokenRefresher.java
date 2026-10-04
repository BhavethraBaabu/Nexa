package com.nexa.integration;

import java.time.Instant;

/** Provider-specific refresh of an expired access token. */
public interface TokenRefresher {

    IntegrationProvider provider();

    Tokens refresh(String refreshToken);

    record Tokens(String accessToken, String refreshToken, Instant expiresAt) {
    }
}
