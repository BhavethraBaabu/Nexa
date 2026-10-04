package com.nexa.user.dto;

import com.nexa.organization.Organization;
import com.nexa.user.Role;
import com.nexa.user.User;

import java.util.UUID;

public record MeResponse(UUID id, String name, String email, Role role, OrganizationSummary organization) {

    public record OrganizationSummary(UUID id, String name) {
    }

    public static MeResponse from(User user, Organization organization) {
        return new MeResponse(user.getId(), user.getName(), user.getEmail(), user.getRole(),
                new OrganizationSummary(organization.getId(), organization.getName()));
    }
}
