package com.nexa.auth;

import com.nexa.common.security.AuthenticatedUser;
import com.nexa.user.User;
import com.nexa.user.UserRepository;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Turns a validated JWT into an {@link AuthenticatedUser}. The user is reloaded so that
 * disabled users and role changes are enforced immediately, not when the token expires.
 */
@Component
public class JwtUserAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final UserRepository userRepository;

    public JwtUserAuthenticationConverter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        UUID userId = parseUuid(jwt.getSubject());
        UUID organizationId = parseUuid(jwt.getClaimAsString(AccessTokenService.ORG_CLAIM));

        User user = userRepository.findById(userId)
                .filter(User::isActive)
                .filter(u -> u.getOrganizationId().equals(organizationId))
                .orElseThrow(() -> new InvalidBearerTokenException("Invalid access token"));

        AuthenticatedUser principal = new AuthenticatedUser(
                user.getId(), user.getOrganizationId(), user.getEmail(), user.getRole());
        return UsernamePasswordAuthenticationToken.authenticated(
                principal, jwt, List.of(new SimpleGrantedAuthority(user.getRole().authority())));
    }

    private static UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new InvalidBearerTokenException("Invalid access token");
        }
    }
}
