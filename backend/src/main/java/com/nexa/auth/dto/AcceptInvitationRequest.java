package com.nexa.auth.dto;

import com.nexa.common.security.PasswordPolicy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AcceptInvitationRequest(
        @NotBlank(message = "Token is required") @Size(max = 128) String token,

        @NotBlank(message = "Name is required")
        @Size(max = 120, message = "Name must be at most 120 characters")
        String name,

        @NotBlank(message = "Password is required")
        @Size(min = PasswordPolicy.MIN_LENGTH, max = PasswordPolicy.MAX_LENGTH, message = PasswordPolicy.MESSAGE)
        String password
) {

    @Override
    public String toString() {
        return "AcceptInvitationRequest[]";
    }
}
