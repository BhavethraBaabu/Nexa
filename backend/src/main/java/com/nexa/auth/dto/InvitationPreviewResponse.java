package com.nexa.auth.dto;

import com.nexa.user.Role;

public record InvitationPreviewResponse(String email, Role role, String organizationName, String invitedByName) {
}
