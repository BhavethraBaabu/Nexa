package com.nexa.common.security;

import com.nexa.user.Role;

import java.util.UUID;

/**
 * The principal for every authenticated request. Loaded from the database on each request,
 * so role changes and removals take effect immediately. {@link #organizationId()} is the
 * tenant boundary for all organization-owned queries (PRD section 39).
 */
public record AuthenticatedUser(UUID userId, UUID organizationId, String email, Role role) {
}
