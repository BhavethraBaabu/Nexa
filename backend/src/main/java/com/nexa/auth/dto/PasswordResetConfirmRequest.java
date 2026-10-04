package com.nexa.auth.dto;

import com.nexa.common.security.PasswordPolicy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordResetConfirmRequest(
        @NotBlank(message = "Token is required") @Size(max = 128) String token,

        @NotBlank(message = "Password is required")
        @Size(min = PasswordPolicy.MIN_LENGTH, max = PasswordPolicy.MAX_LENGTH, message = PasswordPolicy.MESSAGE)
        String newPassword
) {

    @Override
    public String toString() {
        return "PasswordResetConfirmRequest[]";
    }
}
