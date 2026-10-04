package com.nexa.organization.dto;

import com.nexa.user.Role;
import com.nexa.user.User;

import java.time.Instant;
import java.util.UUID;

public record MemberResponse(UUID id, String name, String email, Role role, Instant joinedAt) {

    public static MemberResponse from(User user) {
        return new MemberResponse(user.getId(), user.getName(), user.getEmail(), user.getRole(), user.getCreatedAt());
    }
}
