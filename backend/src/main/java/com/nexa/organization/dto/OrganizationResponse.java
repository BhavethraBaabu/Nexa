package com.nexa.organization.dto;

import java.time.Instant;
import java.util.UUID;

public record OrganizationResponse(UUID id, String name, long memberCount, Instant createdAt) {
}
