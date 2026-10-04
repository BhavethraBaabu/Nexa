package com.nexa.organization.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateOrganizationRequest(
        @NotBlank(message = "Organization name is required")
        @Size(max = 120, message = "Organization name must be at most 120 characters")
        String name
) {
}
