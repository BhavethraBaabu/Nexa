package com.nexa.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "nexa.auth")
public record AuthProperties(
        /** HMAC-SHA256 signing key for access tokens. Must be at least 32 bytes. */
        @NotBlank String jwtSecret,
        @NotBlank String issuer,
        @NotNull Duration accessTokenTtl,
        @NotNull Duration refreshTokenTtl,
        @NotNull Duration passwordResetTtl,
        @NotNull Duration invitationTtl,
        /** Base URL of the web app, used to build links in emails. */
        @NotBlank String frontendBaseUrl,
        @Valid @NotNull RefreshCookie refreshCookie
) {

    public record RefreshCookie(@NotBlank String name, boolean secure, @NotBlank String sameSite, @NotBlank String path) {
    }
}
