package com.nexa.auth.dto;

import com.nexa.user.dto.MeResponse;

/**
 * Returned by login, register, refresh and invitation acceptance. The refresh token is
 * never in the body; it is set as an httpOnly cookie.
 */
public record AuthResponse(String accessToken, String tokenType, long expiresIn, MeResponse user) {

    public static AuthResponse bearer(String accessToken, long expiresIn, MeResponse user) {
        return new AuthResponse(accessToken, "Bearer", expiresIn, user);
    }
}
