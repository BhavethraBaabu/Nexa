package com.nexa.organization;

import com.nexa.audit.AuditAction;
import com.nexa.audit.AuditService;
import com.nexa.auth.RefreshTokenService;
import com.nexa.common.exception.BusinessRuleException;
import com.nexa.common.exception.ResourceNotFoundException;
import com.nexa.common.security.AuthenticatedUser;
import com.nexa.organization.dto.MemberResponse;
import com.nexa.user.Role;
import com.nexa.user.User;
import com.nexa.user.UserRepository;
import com.nexa.user.UserStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Organization membership. Every lookup is scoped to the caller's organization, so a user ID
 * from another tenant behaves exactly like a missing one (404), revealing nothing.
 */
@Service
public class MemberService {

    private final UserRepository userRepository;
    private final RefreshTokenService refreshTokenService;
    private final AuditService auditService;
    private final Clock clock;

    public MemberService(UserRepository userRepository, RefreshTokenService refreshTokenService,
                         AuditService auditService, Clock clock) {
        this.userRepository = userRepository;
        this.refreshTokenService = refreshTokenService;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<MemberResponse> list(AuthenticatedUser current) {
        return userRepository.findByOrganizationIdAndStatusOrderByNameAsc(current.organizationId(), UserStatus.ACTIVE)
                .stream()
                .map(MemberResponse::from)
                .toList();
    }

    @Transactional
    public MemberResponse changeRole(AuthenticatedUser current, UUID memberId, Role newRole) {
        User member = loadActiveMember(current, memberId);
        Role previous = member.getRole();
        if (previous == newRole) {
            return MemberResponse.from(member);
        }
        if (previous == Role.ADMIN) {
            ensureAnotherAdminRemains(current.organizationId());
        }
        member.changeRole(newRole, clock.instant());
        auditService.record(current.organizationId(), current.userId(), AuditAction.MEMBER_ROLE_CHANGED, "USER",
                member.getId(), Map.of("from", previous.name(), "to", newRole.name()));
        return MemberResponse.from(member);
    }

    @Transactional
    public void remove(AuthenticatedUser current, UUID memberId) {
        if (current.userId().equals(memberId)) {
            throw new BusinessRuleException("You cannot remove yourself from the organization");
        }
        User member = loadActiveMember(current, memberId);
        if (member.getRole() == Role.ADMIN) {
            ensureAnotherAdminRemains(current.organizationId());
        }
        Instant now = clock.instant();
        member.disable(now);
        refreshTokenService.revokeAllForUser(member.getId());
        auditService.record(current.organizationId(), current.userId(), AuditAction.MEMBER_REMOVED, "USER",
                member.getId(), Map.of("email", member.getEmail()));
    }

    private User loadActiveMember(AuthenticatedUser current, UUID memberId) {
        return userRepository.findByIdAndOrganizationId(memberId, current.organizationId())
                .filter(User::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Member", memberId));
    }

    private void ensureAnotherAdminRemains(UUID organizationId) {
        long admins = userRepository.countByOrganizationIdAndRoleAndStatus(organizationId, Role.ADMIN, UserStatus.ACTIVE);
        if (admins <= 1) {
            throw new BusinessRuleException("An organization must have at least one admin");
        }
    }
}
