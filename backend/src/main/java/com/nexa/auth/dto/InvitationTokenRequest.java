package com.nexa.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record InvitationTokenRequest(@NotBlank(message = "Token is required") @Size(max = 128) String token) {

    @Override
    public String toString() {
        return "InvitationTokenRequest[]";
    }
}
