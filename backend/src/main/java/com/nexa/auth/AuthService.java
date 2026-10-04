package com.nexa.auth;

import com.nexa.audit.AuditAction;
import com.nexa.audit.AuditService;
import com.nexa.auth.dto.AcceptInvitationRequest;
import com.nexa.auth.dto.AuthResponse;
import com.nexa.auth.dto.InvitationPreviewResponse;
import com.nexa.auth.dto.LoginRequest;
import com.nexa.auth.dto.PasswordResetConfirmRequest;
import com.nexa.auth.dto.RegisterRequest;
import com.nexa.common.exception.ConflictException;
import com.nexa.common.exception.InvalidTokenException;
import com.nexa.common.exception.UnauthorizedException;
import com.nexa.common.security.SecureTokens;
import com.nexa.notification.Mailer;
import com.nexa.organization.Invitation;
import com.nexa.organization.InvitationRepository;
import com.nexa.organization.Organization;
import com.nexa.organization.OrganizationRepository;
import com.nexa.user.Role;
import com.nexa.user.User;
import com.nexa.user.UserRepository;
import com.nexa.user.UserService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

@Service
public class AuthService {

    private static final String INVALID_CREDENTIALS = "Invalid email or password";
    private static final String INVALID_RESET_TOKEN = "This password reset link is invalid or has expired";
    private static final String INVALID_INVITATION = "This invitation is invalid or has expired";

    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final InvitationRepository invitationRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final RefreshTokenService refreshTokenService;
    private final AccessTokenService accessTokenService;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final Mailer mailer;
    private final AuthProperties properties;
    private final Clock clock;
    /** Compared against when the email is unknown, so login timing does not reveal registered emails. */
    private final String dummyPasswordHash;

    public AuthService(UserRepository userRepository, OrganizationRepository organizationRepository,
                       InvitationRepository invitationRepository,
                       PasswordResetTokenRepository passwordResetTokenRepository,
                       RefreshTokenService refreshTokenService, AccessTokenService accessTokenService,
                       UserService userService, PasswordEncoder passwordEncoder, AuditService auditService,
                       Mailer mailer, AuthProperties properties, Clock clock) {
        this.userRepository = userRepository;
        this.organizationRepository = organizationRepository;
        this.invitationRepository = invitationRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.refreshTokenService = refreshTokenService;
        this.accessTokenService = accessTokenService;
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.mailer = mailer;
        this.properties = properties;
        this.clock = clock;
        this.dummyPasswordHash = passwordEncoder.encode(SecureTokens.generate());
    }

    /** Creates a new organization with the registering user as its first ADMIN. */
    @Transactional
    public AuthSession register(RegisterRequest request) {
        String email = User.normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("An account with this email already exists");
        }
        Instant now = clock.instant();
        Organization organization = organizationRepository.save(
                Organization.create(request.organizationName().trim(), now));
        User user = userRepository.save(User.create(organization.getId(), request.name().trim(), email,
                passwordEncoder.encode(request.password()), Role.ADMIN, now));

        auditService.record(organization.getId(), user.getId(), AuditAction.ORGANIZATION_CREATED,
                "ORGANIZATION", organization.getId());
        auditService.record(organization.getId(), user.getId(), AuditAction.USER_REGISTERED, "USER", user.getId());
        return startSession(user);
    }

    @Transactional(noRollbackFor = UnauthorizedException.class)
    public AuthSession login(LoginRequest request) {
        Optional<User> found = userRepository.findByEmail(User.normalizeEmail(request.email()));
        if (found.isEmpty()) {
            passwordEncoder.matches(request.password(), dummyPasswordHash);
            throw new UnauthorizedException(INVALID_CREDENTIALS);
        }
        User user = found.get();
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash()) || !user.isActive()) {
            auditService.record(user.getOrganizationId(), user.getId(), AuditAction.USER_LOGIN_FAILED, "USER", user.getId());
            throw new UnauthorizedException(INVALID_CREDENTIALS);
        }
        auditService.record(user.getOrganizationId(), user.getId(), AuditAction.USER_LOGGED_IN, "USER", user.getId());
        return startSession(user);
    }

    @Transactional(noRollbackFor = UnauthorizedException.class)
    public AuthSession refresh(String rawRefreshToken) {
        RefreshTokenService.Rotation rotation = refreshTokenService.rotate(rawRefreshToken);
        return new AuthSession(buildResponse(rotation.user()), rotation.refreshToken());
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokenService.revoke(rawRefreshToken)
                .flatMap(userRepository::findById)
                .ifPresent(user -> auditService.record(user.getOrganizationId(), user.getId(),
                        AuditAction.USER_LOGGED_OUT, "USER", user.getId()));
    }

    /**
     * Sends a reset link if an active account exists. The caller always gets the same
     * response, so this cannot be used to discover registered emails.
     */
    @Transactional
    public void requestPasswordReset(String email) {
        userRepository.findByEmail(User.normalizeEmail(email))
                .filter(User::isActive)
                .ifPresent(user -> {
                    Instant now = clock.instant();
                    passwordResetTokenRepository.invalidateAllForUser(user.getId(), now);
                    String raw = SecureTokens.generate();
                    passwordResetTokenRepository.save(PasswordResetToken.create(user.getId(), SecureTokens.hash(raw),
                            now.plus(properties.passwordResetTtl()), now));
                    auditService.record(user.getOrganizationId(), user.getId(),
                            AuditAction.PASSWORD_RESET_REQUESTED, "USER", user.getId());
                    mailer.sendPasswordReset(user.getEmail(), link("/reset-password", raw));
                });
    }

    /** Sets a new password and signs the user out of every session. */
    @Transactional
    public void confirmPasswordReset(PasswordResetConfirmRequest request) {
        Instant now = clock.instant();
        PasswordResetToken token = passwordResetTokenRepository.findByTokenHash(SecureTokens.hash(request.token()))
                .filter(t -> t.isUsable(now))
                .orElseThrow(() -> new InvalidTokenException(INVALID_RESET_TOKEN));
        User user = userRepository.findById(token.getUserId())
                .filter(User::isActive)
                .orElseThrow(() -> new InvalidTokenException(INVALID_RESET_TOKEN));

        token.markUsed(now);
        user.changePasswordHash(passwordEncoder.encode(request.newPassword()), now);
        refreshTokenService.revokeAllForUser(user.getId());
        auditService.record(user.getOrganizationId(), user.getId(), AuditAction.PASSWORD_RESET_COMPLETED, "USER", user.getId());
    }

    @Transactional(readOnly = true)
    public InvitationPreviewResponse previewInvitation(String rawToken) {
        Invitation invitation = findPendingInvitation(rawToken);
        Organization organization = organizationRepository.findById(invitation.getOrganizationId())
                .orElseThrow(() -> new InvalidTokenException(INVALID_INVITATION));
        String inviterName = userRepository.findById(invitation.getInvitedBy()).map(User::getName).orElse(null);
        return new InvitationPreviewResponse(invitation.getEmail(), invitation.getRole(), organization.getName(), inviterName);
    }

    /** Creates the invited user's account in the inviting organization and signs them in. */
    @Transactional
    public AuthSession acceptInvitation(AcceptInvitationRequest request) {
        Invitation invitation = findPendingInvitation(request.token());
        if (userRepository.existsByEmail(invitation.getEmail())) {
            throw new ConflictException("An account with this email already exists");
        }
        Instant now = clock.instant();
        User user = userRepository.save(User.create(invitation.getOrganizationId(), request.name().trim(),
                invitation.getEmail(), passwordEncoder.encode(request.password()), invitation.getRole(), now));
        invitation.accept(now);
        auditService.record(invitation.getOrganizationId(), user.getId(), AuditAction.INVITATION_ACCEPTED,
                "INVITATION", invitation.getId(), Map.of("role", invitation.getRole().name()));
        return startSession(user);
    }

    private Invitation findPendingInvitation(String rawToken) {
        Instant now = clock.instant();
        return invitationRepository.findByTokenHash(SecureTokens.hash(rawToken))
                .filter(i -> i.isPending(now))
                .orElseThrow(() -> new InvalidTokenException(INVALID_INVITATION));
    }

    private AuthSession startSession(User user) {
        String refreshToken = refreshTokenService.issueNewFamily(user);
        return new AuthSession(buildResponse(user), refreshToken);
    }

    private AuthResponse buildResponse(User user) {
        AccessTokenService.IssuedAccessToken accessToken = accessTokenService.issue(user);
        return AuthResponse.bearer(accessToken.value(), accessToken.expiresInSeconds(), userService.toMe(user));
    }

    private String link(String path, String token) {
        return UriComponentsBuilder.fromUriString(properties.frontendBaseUrl())
                .path(path)
                .queryParam("token", token)
                .build()
                .toUriString();
    }
}
