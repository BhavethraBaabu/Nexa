package com.nexa.organization.dto;

import com.nexa.user.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record InviteMemberRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        @Size(max = 254, message = "Email must be at most 254 characters")
        String email,

        @NotNull(message = "Role is required")
        Role role
) {
}
