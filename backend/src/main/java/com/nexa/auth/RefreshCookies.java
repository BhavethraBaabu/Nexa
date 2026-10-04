package com.nexa.auth;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Builds the refresh-token cookie: httpOnly (not readable by JavaScript), SameSite
 * (not sent on cross-site requests, which protects the cookie-authenticated refresh and
 * logout endpoints from CSRF) and scoped to the auth endpoints only.
 */
@Component
public class RefreshCookies {

    private final AuthProperties properties;

    public RefreshCookies(AuthProperties properties) {
        this.properties = properties;
    }

    public String name() {
        return properties.refreshCookie().name();
    }

    public ResponseCookie create(String refreshToken) {
        return base(refreshToken).maxAge(properties.refreshTokenTtl()).build();
    }

    public ResponseCookie clear() {
        return base("").maxAge(Duration.ZERO).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        AuthProperties.RefreshCookie cookie = properties.refreshCookie();
        return ResponseCookie.from(cookie.name(), value)
                .httpOnly(true)
                .secure(cookie.secure())
                .sameSite(cookie.sameSite())
                .path(cookie.path());
    }
}
