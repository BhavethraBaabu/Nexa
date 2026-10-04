package com.nexa.organization.dto;

import com.nexa.organization.Invitation;
import com.nexa.user.Role;

import java.time.Instant;
import java.util.UUID;

public record InvitationResponse(UUID id, String email, Role role, UUID invitedBy, Instant expiresAt, Instant createdAt) {

    public static InvitationResponse from(Invitation invitation) {
        return new InvitationResponse(invitation.getId(), invitation.getEmail(), invitation.getRole(),
                invitation.getInvitedBy(), invitation.getExpiresAt(), invitation.getCreatedAt());
    }
}
