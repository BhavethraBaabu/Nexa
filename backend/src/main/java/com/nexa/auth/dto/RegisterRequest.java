package com.nexa.auth.dto;

import com.nexa.common.security.PasswordPolicy;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "Name is required")
        @Size(max = 120, message = "Name must be at most 120 characters")
        String name,

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        @Size(max = 254, message = "Email must be at most 254 characters")
        String email,

        @NotBlank(message = "Password is required")
        @Size(min = PasswordPolicy.MIN_LENGTH, max = PasswordPolicy.MAX_LENGTH, message = PasswordPolicy.MESSAGE)
        String password,

        @NotBlank(message = "Organization name is required")
        @Size(max = 120, message = "Organization name must be at most 120 characters")
        String organizationName
) {

    @Override
    public String toString() {
        return "RegisterRequest[email=" + email + "]";
    }
}
