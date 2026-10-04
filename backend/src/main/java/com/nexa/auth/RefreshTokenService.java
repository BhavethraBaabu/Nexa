package com.nexa.auth;

import com.nexa.audit.AuditAction;
import com.nexa.audit.AuditService;
import com.nexa.common.exception.UnauthorizedException;
import com.nexa.common.security.SecureTokens;
import com.nexa.user.User;
import com.nexa.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Opaque refresh tokens with rotation and reuse detection. Each refresh revokes the presented
 * token and issues a successor in the same family. Presenting an already-revoked token means
 * it was stolen or replayed, so the whole family is revoked.
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
    private static final String INVALID_SESSION = "Your session has expired. Please sign in again.";

    private final RefreshTokenRepository repository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final AuthProperties properties;
    private final Clock clock;

    public RefreshTokenService(RefreshTokenRepository repository, UserRepository userRepository,
                               AuditService auditService, AuthProperties properties, Clock clock) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public String issueNewFamily(User user) {
        return issue(user.getId(), UUID.randomUUID(), clock.instant()).raw();
    }

    /**
     * Validates and rotates a refresh token.
     *
     * @return the user and the new raw refresh token
     */
    @Transactional(noRollbackFor = UnauthorizedException.class)
    public Rotation rotate(String rawToken) {
        Instant now = clock.instant();
        RefreshToken current = find(rawToken).orElseThrow(() -> new UnauthorizedException(INVALID_SESSION));

        if (current.isRevoked()) {
            int revoked = repository.revokeFamily(current.getFamilyId(), now);
            userRepository.findById(current.getUserId()).ifPresent(user ->
                    auditService.record(user.getOrganizationId(), user.getId(),
                            AuditAction.REFRESH_TOKEN_REUSE_DETECTED, "USER", user.getId(),
                            Map.of("revokedTokens", revoked)));
            log.warn("Refresh token reuse detected for user {}; revoked {} tokens", current.getUserId(), revoked);
            throw new UnauthorizedException(INVALID_SESSION);
        }
        if (current.isExpired(now)) {
            throw new UnauthorizedException(INVALID_SESSION);
        }
        User user = userRepository.findById(current.getUserId())
                .filter(User::isActive)
                .orElseThrow(() -> new UnauthorizedException(INVALID_SESSION));

        Issued successor = issue(user.getId(), current.getFamilyId(), now);
        current.rotateTo(successor.id(), now);
        return new Rotation(user, successor.raw());
    }

    @Transactional
    public Optional<UUID> revoke(String rawToken) {
        return find(rawToken).map(token -> {
            token.revoke(clock.instant());
            return token.getUserId();
        });
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void revokeAllForUser(UUID userId) {
        repository.revokeAllForUser(userId, clock.instant());
    }

    private Optional<RefreshToken> find(String rawToken) {
        if (rawToken == null || rawToken.isBlank() || rawToken.length() > 128) {
            return Optional.empty();
        }
        return repository.findByTokenHash(SecureTokens.hash(rawToken));
    }

    private Issued issue(UUID userId, UUID familyId, Instant now) {
        String raw = SecureTokens.generate();
        RefreshToken token = RefreshToken.create(userId, familyId, SecureTokens.hash(raw),
                now.plus(properties.refreshTokenTtl()), now);
        repository.save(token);
        return new Issued(token.getId(), raw);
    }

    private record Issued(UUID id, String raw) {
    }

    public record Rotation(User user, String refreshToken) {

        @Override
        public String toString() {
            return "Rotation[userId=" + user.getId() + "]";
        }
    }
}
