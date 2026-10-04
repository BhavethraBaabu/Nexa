package com.nexa.integration;

import com.nexa.auth.AuthProperties;
import com.nexa.common.exception.InvalidTokenException;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Signed, short-lived OAuth {@code state} values. The callback arrives as a plain browser
 * redirect with no access token, so the state carries (and proves) who started the flow and for
 * which organization and provider. It also prevents login-CSRF: a state minted for one provider
 * or by another server is rejected.
 */
@Service
public class OAuthStateService {

    private static final Duration TTL = Duration.ofMinutes(10);
    private static final String PURPOSE = "integration-oauth";

    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    private final AuthProperties authProperties;
    private final Clock clock;

    public OAuthStateService(JwtEncoder encoder, JwtDecoder decoder, AuthProperties authProperties, Clock clock) {
        this.encoder = encoder;
        this.decoder = decoder;
        this.authProperties = authProperties;
        this.clock = clock;
    }

    public record State(UUID organizationId, UUID userId, IntegrationProvider provider) {
    }

    public String issue(UUID organizationId, UUID userId, IntegrationProvider provider) {
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(authProperties.issuer())
                .subject(userId.toString())
                .issuedAt(now)
                .expiresAt(now.plus(TTL))
                .id(UUID.randomUUID().toString())
                .claim("purpose", PURPOSE)
                .claim("org", organizationId.toString())
                .claim("provider", provider.name())
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    public State verify(String state, IntegrationProvider expectedProvider) {
        try {
            Jwt jwt = decoder.decode(state);
            if (!PURPOSE.equals(jwt.getClaimAsString("purpose")) || !expectedProvider.name().equals(jwt.getClaimAsString("provider"))) {
                throw new InvalidTokenException("Invalid connection request");
            }
            return new State(UUID.fromString(jwt.getClaimAsString("org")), UUID.fromString(jwt.getSubject()), expectedProvider);
        } catch (JwtException | IllegalArgumentException | NullPointerException e) {
            throw new InvalidTokenException("This connection link has expired. Please start again from Integrations.");
        }
    }
}
