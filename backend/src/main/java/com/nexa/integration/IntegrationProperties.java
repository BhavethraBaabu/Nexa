package com.nexa.integration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * OAuth app credentials and endpoints. Base URLs are configurable so tests (and proxies) can
 * point at stand-ins; client IDs are blank until an admin registers the apps.
 */
@Validated
@ConfigurationProperties(prefix = "nexa.integrations")
public record IntegrationProperties(
        /** Base64-encoded 256-bit key for encrypting stored tokens (INTEGRATION_ENCRYPTION_KEY). */
        @NotBlank String encryptionKey,
        /** Public URL of this API, used to build OAuth redirect URIs. */
        @NotBlank String callbackBaseUrl,
        /** Where the browser is sent after an OAuth flow finishes. */
        @NotBlank String frontendBaseUrl,
        @Valid @NotNull Jira jira,
        @Valid @NotNull Slack slack
) {

    public record Jira(String clientId, String clientSecret, @NotBlank String authBaseUrl, @NotBlank String apiBaseUrl) {

        public boolean configured() {
            return clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank();
        }
    }

    public record Slack(String clientId, String clientSecret, @NotBlank String baseUrl) {

        public boolean configured() {
            return clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank();
        }
    }

    public String redirectUri(IntegrationProvider provider) {
        return callbackBaseUrl.replaceAll("/+$", "") + "/api/v1/integrations/" + provider.name().toLowerCase() + "/callback";
    }
}
