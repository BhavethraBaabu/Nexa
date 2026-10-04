package com.nexa.organization;

import com.nexa.audit.AuditAction;
import com.nexa.audit.AuditService;
import com.nexa.auth.AuthProperties;
import com.nexa.common.exception.ConflictException;
import com.nexa.common.exception.ResourceNotFoundException;
import com.nexa.common.security.AuthenticatedUser;
import com.nexa.common.security.SecureTokens;
import com.nexa.notification.Mailer;
import com.nexa.organization.dto.InvitationResponse;
import com.nexa.organization.dto.InviteMemberRequest;
import com.nexa.user.User;
import com.nexa.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class InvitationService {

    private final InvitationRepository invitationRepository;
    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final Mailer mailer;
    private final AuthProperties authProperties;
    private final Clock clock;

    public InvitationService(InvitationRepository invitationRepository, OrganizationRepository organizationRepository,
                             UserRepository userRepository, AuditService auditService, Mailer mailer,
                             AuthProperties authProperties, Clock clock) {
        this.invitationRepository = invitationRepository;
        this.organizationRepository = organizationRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.mailer = mailer;
        this.authProperties = authProperties;
        this.clock = clock;
    }

    /** Creates an invitation, replacing any pending invitation for the same email. */
    @Transactional
    public InvitationResponse invite(AuthenticatedUser current, InviteMemberRequest request) {
        String email = User.normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("This person already has a Nexa account");
        }
        Instant now = clock.instant();
        invitationRepository.revokePendingForEmail(current.organizationId(), email, now);

        String rawToken = SecureTokens.generate();
        Invitation invitation = invitationRepository.save(Invitation.create(current.organizationId(), email,
                request.role(), SecureTokens.hash(rawToken), current.userId(),
                now.plus(authProperties.invitationTtl()), now));
        auditService.record(current.organizationId(), current.userId(), AuditAction.MEMBER_INVITED, "INVITATION",
                invitation.getId(), Map.of("email", email, "role", request.role().name()));

        Organization organization = organizationRepository.findById(current.organizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Organization", current.organizationId()));
        String inviterName = userRepository.findById(current.userId()).map(User::getName).orElse("A teammate");
        mailer.sendInvitation(email, organization.getName(), inviterName, acceptLink(rawToken));
        return InvitationResponse.from(invitation);
    }

    @Transactional(readOnly = true)
    public List<InvitationResponse> listPending(AuthenticatedUser current) {
        return invitationRepository.findPending(current.organizationId(), clock.instant()).stream()
                .map(InvitationResponse::from)
                .toList();
    }

    @Transactional
    public void revoke(AuthenticatedUser current, UUID invitationId) {
        Instant now = clock.instant();
        Invitation invitation = invitationRepository.findByIdAndOrganizationId(invitationId, current.organizationId())
                .filter(i -> i.isPending(now))
                .orElseThrow(() -> new ResourceNotFoundException("Invitation", invitationId));
        invitation.revoke(now);
        auditService.record(current.organizationId(), current.userId(), AuditAction.INVITATION_REVOKED, "INVITATION",
                invitation.getId(), Map.of("email", invitation.getEmail()));
    }

    private String acceptLink(String rawToken) {
        return UriComponentsBuilder.fromUriString(authProperties.frontendBaseUrl())
                .path("/accept-invite")
                .queryParam("token", rawToken)
                .build()
                .toUriString();
    }
}
