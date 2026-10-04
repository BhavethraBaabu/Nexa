package com.nexa.auth;

import com.nexa.support.IntegrationTest;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MvcResult;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthIntegrationTest extends IntegrationTest {

    private static final String TEST_SECRET = "test-only-secret-0123456789abcdef0123456789";

    // --- Registration ---------------------------------------------------------------------

    @Test
    void registerCreatesOrganizationWithAdminAndStartsSession() throws Exception {
        MvcResult result = mockMvc.perform(json(post("/api/v1/auth/register"), Map.of(
                        "name", "Alice", "email", "Alice@Acme.com", "password", PASSWORD, "organizationName", "Acme")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.user.email").value("alice@acme.com"))
                .andExpect(jsonPath("$.user.role").value("ADMIN"))
                .andExpect(jsonPath("$.user.organization.name").value("Acme"))
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn();

        String setCookie = result.getResponse().getHeader("Set-Cookie");
        assertThat(setCookie).contains("nexa_refresh=", "HttpOnly", "SameSite=Strict", "Path=/api/v1/auth");

        Integer audits = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM audit_logs WHERE action IN ('ORGANIZATION_CREATED', 'USER_REGISTERED')", Integer.class);
        assertThat(audits).isEqualTo(2);
    }

    @Test
    void passwordsAreStoredHashed() throws Exception {
        register("Alice", "alice@acme.com", "Acme");

        String hash = jdbcTemplate.queryForObject("SELECT password_hash FROM users", String.class);
        assertThat(hash).startsWith("{bcrypt}").doesNotContain(PASSWORD);
    }

    @Test
    void registerRejectsDuplicateEmailCaseInsensitively() throws Exception {
        register("Alice", "alice@acme.com", "Acme");

        mockMvc.perform(json(post("/api/v1/auth/register"), Map.of(
                        "name", "Imposter", "email", "ALICE@acme.com", "password", PASSWORD, "organizationName", "Evil")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONFLICT"));
    }

    @Test
    void registerValidatesInput() throws Exception {
        mockMvc.perform(json(post("/api/v1/auth/register"), Map.of(
                        "name", "", "email", "not-an-email", "password", "short", "organizationName", "Acme")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.violations.length()").value(3));
    }

    // --- Login ----------------------------------------------------------------------------

    @Test
    void loginSucceedsWithCorrectCredentials() throws Exception {
        register("Alice", "alice@acme.com", "Acme");

        Session session = login("ALICE@acme.com", PASSWORD);

        assertThat(session.accessToken()).isNotBlank();
        assertThat(session.refreshToken()).isNotBlank();
    }

    @Test
    void loginFailuresAreIndistinguishable() throws Exception {
        register("Alice", "alice@acme.com", "Acme");

        mockMvc.perform(json(post("/api/v1/auth/login"), Map.of("email", "alice@acme.com", "password", "wrong-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
        mockMvc.perform(json(post("/api/v1/auth/login"), Map.of("email", "nobody@acme.com", "password", "wrong-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));

        Integer failures = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM audit_logs WHERE action = 'USER_LOGIN_FAILED'", Integer.class);
        assertThat(failures).isEqualTo(1);
    }

    // --- Access tokens --------------------------------------------------------------------

    @Test
    void accessTokenAuthenticatesRequests() throws Exception {
        Session alice = register("Alice", "alice@acme.com", "Acme");

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", alice.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Alice"))
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void missingOrMalformedTokenIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    @Test
    void expiredTokenIsRejected() throws Exception {
        Session alice = register("Alice", "alice@acme.com", "Acme");
        Instant past = Instant.now().minus(1, ChronoUnit.HOURS);

        String expired = sign(TEST_SECRET, alice, past.minus(15, ChronoUnit.MINUTES), past);

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() throws Exception {
        Session alice = register("Alice", "alice@acme.com", "Acme");
        Instant now = Instant.now();

        String forged = sign("attacker-controlled-secret-0123456789abcdef", alice, now, now.plus(15, ChronoUnit.MINUTES));

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + forged))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenWithForgedOrganizationClaimIsRejected() throws Exception {
        Session alice = register("Alice", "alice@acme.com", "Acme");
        Session bob = register("Bob", "bob@globex.com", "Globex");
        Instant now = Instant.now();
        Session aliceClaimingGlobex = new Session(null, null, alice.userId(), bob.organizationId());

        String token = sign(TEST_SECRET, aliceClaimingGlobex, now, now.plus(15, ChronoUnit.MINUTES));

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    // --- Refresh tokens -------------------------------------------------------------------

    @Test
    void refreshRotatesTheRefreshToken() throws Exception {
        Session alice = register("Alice", "alice@acme.com", "Acme");

        MvcResult result = mockMvc.perform(post("/api/v1/auth/refresh").cookie(alice.refreshCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();
        Session refreshed = session(result);

        assertThat(refreshed.refreshToken()).isNotBlank().isNotEqualTo(alice.refreshToken());
        mockMvc.perform(get("/api/v1/users/me").header("Authorization", refreshed.bearer()))
                .andExpect(status().isOk());
    }

    @Test
    void reusingARotatedRefreshTokenRevokesTheWholeSession() throws Exception {
        Session alice = register("Alice", "alice@acme.com", "Acme");
        Session refreshed = session(mockMvc.perform(post("/api/v1/auth/refresh").cookie(alice.refreshCookie()))
                .andExpect(status().isOk())
                .andReturn());

        // An attacker replays the old token: reuse is detected and audited...
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(alice.refreshCookie()))
                .andExpect(status().isUnauthorized());
        Integer reuse = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM audit_logs WHERE action = 'REFRESH_TOKEN_REUSE_DETECTED'", Integer.class);
        assertThat(reuse).isEqualTo(1);

        // ...and the legitimate successor is revoked too, forcing a fresh login.
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(refreshed.refreshCookie()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshWithoutValidCookieFailsAndClearsCookie() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie(REFRESH_COOKIE, "garbage")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"))
                .andReturn();

        assertThat(result.getResponse().getHeader("Set-Cookie")).contains("nexa_refresh=", "Max-Age=0");
        mockMvc.perform(post("/api/v1/auth/refresh")).andExpect(status().isUnauthorized());
    }

    @Test
    void expiredRefreshTokenIsRejected() throws Exception {
        Session alice = register("Alice", "alice@acme.com", "Acme");
        jdbcTemplate.update("UPDATE refresh_tokens SET expires_at = now() - interval '1 minute'");

        mockMvc.perform(post("/api/v1/auth/refresh").cookie(alice.refreshCookie()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesTheRefreshToken() throws Exception {
        Session alice = register("Alice", "alice@acme.com", "Acme");

        MvcResult result = mockMvc.perform(post("/api/v1/auth/logout").cookie(alice.refreshCookie()))
                .andExpect(status().isNoContent())
                .andReturn();
        assertThat(result.getResponse().getHeader("Set-Cookie")).contains("Max-Age=0");

        mockMvc.perform(post("/api/v1/auth/refresh").cookie(alice.refreshCookie()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutWithoutCookieIsHarmless() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")).andExpect(status().isNoContent());
    }

    // --- Password reset -------------------------------------------------------------------

    @Test
    void passwordResetRequestDoesNotRevealWhetherEmailExists() throws Exception {
        mockMvc.perform(json(post("/api/v1/auth/password-reset/request"), Map.of("email", "nobody@acme.com")))
                .andExpect(status().isAccepted());

        assertThat(mailer.resetSent()).isFalse();
    }

    @Test
    void passwordResetChangesPasswordAndEndsAllSessions() throws Exception {
        Session alice = register("Alice", "alice@acme.com", "Acme");

        mockMvc.perform(json(post("/api/v1/auth/password-reset/request"), Map.of("email", "alice@acme.com")))
                .andExpect(status().isAccepted());
        String token = mailer.lastResetToken();

        mockMvc.perform(json(post("/api/v1/auth/password-reset/confirm"),
                        Map.of("token", token, "newPassword", "a-brand-new-password")))
                .andExpect(status().isNoContent());

        mockMvc.perform(json(post("/api/v1/auth/login"), Map.of("email", "alice@acme.com", "password", PASSWORD)))
                .andExpect(status().isUnauthorized());
        login("alice@acme.com", "a-brand-new-password");
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(alice.refreshCookie()))
                .andExpect(status().isUnauthorized());

        // Single use.
        mockMvc.perform(json(post("/api/v1/auth/password-reset/confirm"),
                        Map.of("token", token, "newPassword", "yet-another-password")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_TOKEN"));
    }

    @Test
    void newPasswordResetRequestInvalidatesThePreviousLink() throws Exception {
        register("Alice", "alice@acme.com", "Acme");
        mockMvc.perform(json(post("/api/v1/auth/password-reset/request"), Map.of("email", "alice@acme.com")));
        String first = mailer.lastResetToken();
        mockMvc.perform(json(post("/api/v1/auth/password-reset/request"), Map.of("email", "alice@acme.com")));

        mockMvc.perform(json(post("/api/v1/auth/password-reset/confirm"),
                        Map.of("token", first, "newPassword", "a-brand-new-password")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void expiredPasswordResetTokenIsRejected() throws Exception {
        register("Alice", "alice@acme.com", "Acme");
        mockMvc.perform(json(post("/api/v1/auth/password-reset/request"), Map.of("email", "alice@acme.com")));
        jdbcTemplate.update("UPDATE password_reset_tokens SET expires_at = now() - interval '1 minute'");

        mockMvc.perform(json(post("/api/v1/auth/password-reset/confirm"),
                        Map.of("token", mailer.lastResetToken(), "newPassword", "a-brand-new-password")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_TOKEN"));
    }

    // --- Profile --------------------------------------------------------------------------

    @Test
    void userCanUpdateOwnName() throws Exception {
        Session alice = register("Alice", "alice@acme.com", "Acme");

        mockMvc.perform(json(patch("/api/v1/users/me"), Map.of("name", "Alice Smith"))
                        .header("Authorization", alice.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Alice Smith"));
    }

    private static String sign(String secret, Session session, Instant issuedAt, Instant expiresAt) {
        var key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        var encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("nexa")
                .subject(session.userId().toString())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim("org", session.organizationId().toString())
                .claim("role", "ADMIN")
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
}
