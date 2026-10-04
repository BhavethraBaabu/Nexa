package com.nexa.support;

import com.nexa.notification.Mailer;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base class for API integration tests against the real PostgreSQL test database.
 * Every test starts with empty tables.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(IntegrationTest.TestDoublesConfig.class)
public abstract class IntegrationTest {

    protected static final String PASSWORD = "correct-horse-battery";
    protected static final String REFRESH_COOKIE = "nexa_refresh";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected RecordingMailer mailer;

    @Autowired
    protected FakeLlmClient llm;

    @BeforeEach
    void resetState() {
        jdbcTemplate.execute("""
                TRUNCATE external_actions, ai_actions, integrations, questions, risks, decisions, tasks, meeting_analyses, meeting_participants, meetings,
                    audit_logs, invitations, password_reset_tokens, refresh_tokens, users, organizations CASCADE
                """);
        mailer.clear();
        llm.reset();
    }

    protected Session register(String name, String email, String organizationName) throws Exception {
        MvcResult result = mockMvc.perform(json(post("/api/v1/auth/register"), Map.of(
                        "name", name, "email", email, "password", PASSWORD, "organizationName", organizationName)))
                .andExpect(status().isCreated())
                .andReturn();
        return session(result);
    }

    protected Session login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(json(post("/api/v1/auth/login"), Map.of("email", email, "password", password)))
                .andExpect(status().isOk())
                .andReturn();
        return session(result);
    }

    /** Invites {@code email} as {@code role} and accepts the invitation, returning the new member's session. */
    protected Session inviteAndAccept(Session admin, String name, String email, String role) throws Exception {
        mockMvc.perform(json(post("/api/v1/organizations/members/invite"), Map.of("email", email, "role", role))
                        .header("Authorization", admin.bearer()))
                .andExpect(status().isCreated());
        String token = mailer.lastInvitationToken();
        MvcResult result = mockMvc.perform(json(post("/api/v1/auth/invitations/accept"),
                        Map.of("token", token, "name", name, "password", PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn();
        return session(result);
    }

    protected MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder builder, Object body) {
        return builder.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body));
    }

    protected JsonNode body(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    protected Session session(MvcResult result) throws Exception {
        JsonNode body = body(result);
        Cookie cookie = result.getResponse().getCookie(REFRESH_COOKIE);
        return new Session(
                body.get("accessToken").asString(),
                cookie == null ? null : cookie.getValue(),
                UUID.fromString(body.get("user").get("id").asString()),
                UUID.fromString(body.get("user").get("organization").get("id").asString()));
    }

    public record Session(String accessToken, String refreshToken, UUID userId, UUID organizationId) {

        public String bearer() {
            return "Bearer " + accessToken;
        }

        public Cookie refreshCookie() {
            return new Cookie(REFRESH_COOKIE, refreshToken);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestDoublesConfig {

        @Bean
        @Primary
        RecordingMailer recordingMailer() {
            return new RecordingMailer();
        }

        @Bean
        @Primary
        FakeLlmClient fakeLlmClient() {
            return new FakeLlmClient();
        }
    }

    public static class RecordingMailer implements Mailer {

        private volatile String lastResetLink;
        private volatile String lastInvitationLink;

        @Override
        public void sendPasswordReset(String to, String resetLink) {
            lastResetLink = resetLink;
        }

        @Override
        public void sendInvitation(String to, String organizationName, String inviterName, String acceptLink) {
            lastInvitationLink = acceptLink;
        }

        public String lastResetToken() {
            return tokenFrom(lastResetLink);
        }

        public String lastInvitationToken() {
            return tokenFrom(lastInvitationLink);
        }

        public boolean resetSent() {
            return lastResetLink != null;
        }

        void clear() {
            lastResetLink = null;
            lastInvitationLink = null;
        }

        private static String tokenFrom(String link) {
            if (link == null) {
                throw new IllegalStateException("No email was sent");
            }
            return link.substring(link.indexOf("token=") + "token=".length());
        }
    }
}
