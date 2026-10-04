package com.nexa.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordResetRequest(
        @NotBlank(message = "Email is required") @Email(message = "Email must be valid") @Size(max = 254) String email
) {
}
