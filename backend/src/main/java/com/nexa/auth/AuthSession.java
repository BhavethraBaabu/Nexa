package com.nexa.auth;

import com.nexa.auth.dto.AuthResponse;

/**
 * Result of a successful authentication: the API body plus the raw refresh token, which the
 * controller places in a cookie. Never serialize this type directly.
 */
public record AuthSession(AuthResponse response, String refreshToken) {

    @Override
    public String toString() {
        return "AuthSession[userId=" + response.user().id() + "]";
    }
}
