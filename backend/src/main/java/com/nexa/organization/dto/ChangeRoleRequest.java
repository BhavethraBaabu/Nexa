package com.nexa.organization.dto;

import com.nexa.user.Role;
import jakarta.validation.constraints.NotNull;

public record ChangeRoleRequest(@NotNull(message = "Role is required") Role role) {
}
