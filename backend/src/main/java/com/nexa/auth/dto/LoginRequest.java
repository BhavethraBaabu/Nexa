package com.nexa.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "Email is required") @Size(max = 254) String email,
        @NotBlank(message = "Password is required") @Size(max = 128) String password
) {

    @Override
    public String toString() {
        return "LoginRequest[email=" + email + "]";
    }
}
