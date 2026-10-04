package com.nexa.organization;

import com.nexa.support.IntegrationTest;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OrganizationIntegrationTest extends IntegrationTest {

    // --- Organization ---------------------------------------------------------------------

    @Test
    void currentOrganizationIncludesMemberCount() throws Exception {
        Session alice = register("Alice", "alice@acme.com", "Acme");
        inviteAndAccept(alice, "Bob", "bob@acme.com", "MEMBER");

        mockMvc.perform(get("/api/v1/organizations/current").header("Authorization", alice.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Acme"))
                .andExpect(jsonPath("$.memberCount").value(2));
    }

    @Test
    void onlyAdminsCanRenameOrganization() throws Exception {
        Session alice = register("Alice", "alice@acme.com", "Acme");
        Session bob = inviteAndAccept(alice, "Bob", "bob@acme.com", "MANAGER");

        mockMvc.perform(json(patch("/api/v1/organizations/current"), Map.of("name", "Hijacked"))
                        .header("Authorization", bob.bearer()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
        mockMvc.perform(json(patch("/api/v1/organizations/current"), Map.of("name", "Acme Corp"))
                        .header("Authorization", alice.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Acme Corp"));
    }

    // --- Invitations ----------------------------------------------------------------------

    @Test
    void invitedUserJoinsTheInvitingOrganizationWithTheInvitedRole() throws Exception {
        Session alice = register("Alice", "alice@acme.com", "Acme");

        mockMvc.perform(json(post("/api/v1/organizations/members/invite"), Map.of("email", "Sarah@Acme.com", "role", "MANAGER"))
                        .header("Authorization", alice.bearer()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("sarah@acme.com"))
                .andExpect(jsonPath("$.role").value("MANAGER"));
        String token = mailer.lastInvitationToken();

        mockMvc.perform(json(post("/api/v1/auth/invitations/preview"), Map.of("token", token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organizationName").value("Acme"))
                .andExpect(jsonPath("$.invitedByName").value("Alice"))
                .andExpect(jsonPath("$.email").value("sarah@acme.com"));

        mockMvc.perform(json(post("/api/v1/auth/invitations/accept"),
                        Map.of("token", token, "name", "Sarah", "password", PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.role").value("MANAGER"))
                .andExpect(jsonPath("$.user.organization.id").value(alice.organizationId().toString()));

        mockMvc.perform(get("/api/v1/organizations/members").header("Authorization", alice.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Alice"))
                .andExpect(jsonPath("$[1].name").value("Sarah"));
    }

    @Test
    void invitationCanOnlyBeAcceptedOnce() throws Exception {
        Session alice = register("Alice", "alice@acme.com", "Acme");
        inviteAndAccept(alice, "Bob", "bob@acme.com", "MEMBER");

        mockMvc.perform(json(post("/api/v1/auth/invitations/accept"),
                        Map.of("token", mailer.lastInvitationToken(), "name", "Bob Again", "password", PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_TOKEN"));
    }

    @Test
    void expiredInvitationIsRejected() throws Exception {
        Session alice = register("Alice", "alice@acme.com", "Acme");
        mockMvc.perform(json(post("/api/v1/organizations/members/invite"), Map.of("email", "bob@acme.com", "role", "MEMBER"))
                .header("Authorization", alice.bearer()));
        jdbcTemplate.update("UPDATE invitations SET expires_at = now() - interval '1 minute'");

        mockMvc.perform(json(post("/api/v1/auth/invitations/preview"), Map.of("token", mailer.lastInvitationToken())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cannotInviteSomeoneWhoAlreadyHasAnAccount() throws Exception {
        Session alice = register("Alice", "alice@acme.com", "Acme");
        register("Bob", "bob@globex.com", "Globex");

        mockMvc.perform(json(post("/api/v1/organizations/members/invite"), Map.of("email", "bob@globex.com", "role", "MEMBER"))
                        .header("Authorization", alice.bearer()))
                .andExpect(status().isConflict());
    }

    @Test
    void reinvitingReplacesThePreviousInvitation() throws Exception {
        Session alice = register("Alice", "alice@acme.com", "Acme");
        invite(alice, "bob@acme.com", "MEMBER");
        String first = mailer.lastInvitationToken();
        invite(alice, "bob@acme.com", "MANAGER");

        mockMvc.perform(json(post("/api/v1/auth/invitations/preview"), Map.of("token", first)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/organizations/invitations").header("Authorization", alice.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].role").value("MANAGER"));
    }

    @Test
    void revokedInvitationCannotBeUsed() throws Exception {
        Session alice = register("Alice", "alice@acme.com", "Acme");
        String invitationId = invite(alice, "bob@acme.com", "MEMBER");

        mockMvc.perform(delete("/api/v1/organizations/invitations/" + invitationId).header("Authorization", alice.bearer()))
                .andExpect(status().isNoContent());
        mockMvc.perform(json(post("/api/v1/auth/invitations/preview"), Map.of("token", mailer.lastInvitationToken())))
                .andExpect(status().isBadRequest());
    }

    // --- RBAC -----------------------------------------------------------------------------

    @Test
    void membersAndManagersCannotManageMembership() throws Exception {
        Session alice = register("Alice", "alice@acme.com", "Acme");
        Session manager = inviteAndAccept(alice, "Mia", "mia@acme.com", "MANAGER");
        Session member = inviteAndAccept(alice, "John", "john@acme.com", "MEMBER");

        for (Session session : new Session[]{manager, member}) {
            mockMvc.perform(json(post("/api/v1/organizations/members/invite"), Map.of("email", "x@acme.com", "role", "ADMIN"))
                            .header("Authorization", session.bearer()))
                    .andExpect(status().isForbidden());
            mockMvc.perform(json(patch("/api/v1/organizations/members/" + session.userId()), Map.of("role", "ADMIN"))
                            .header("Authorization", session.bearer()))
                    .andExpect(status().isForbidden());
            mockMvc.perform(delete("/api/v1/organizations/members/" + alice.userId()).header("Authorization", session.bearer()))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/v1/organizations/invitations").header("Authorization", session.bearer()))
                    .andExpect(status().isForbidden());
        }
        // Everyone can see who is in their organization.
        mockMvc.perform(get("/api/v1/organizations/members").header("Authorization", member.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    void adminCanChangeRolesAndChangeTakesEffectImmediately() throws Exception {
        Session alice = register("Alice", "alice@acme.com", "Acme");
        Session bob = inviteAndAccept(alice, "Bob", "bob@acme.com", "MEMBER");

        mockMvc.perform(json(patch("/api/v1/organizations/members/" + bob.userId()), Map.of("role", "ADMIN"))
                        .header("Authorization", alice.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));

        // Bob's existing access token now carries admin rights, because roles are loaded per request.
        mockMvc.perform(get("/api/v1/organizations/invitations").header("Authorization", bob.bearer()))
                .andExpect(status().isOk());
    }

    @Test
    void lastAdminCannotBeDemoted() throws Exception {
        Session alice = register("Alice", "alice@acme.com", "Acme");

        mockMvc.perform(json(patch("/api/v1/organizations/members/" + alice.userId()), Map.of("role", "MEMBER"))
                        .header("Authorization", alice.bearer()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error").value("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    void adminCannotRemoveThemselves() throws Exception {
        Session alice = register("Alice", "alice@acme.com", "Acme");

        mockMvc.perform(delete("/api/v1/organizations/members/" + alice.userId()).header("Authorization", alice.bearer()))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void removedMemberLosesAccessImmediately() throws Exception {
        Session alice = register("Alice", "alice@acme.com", "Acme");
        Session bob = inviteAndAccept(alice, "Bob", "bob@acme.com", "MEMBER");

        mockMvc.perform(delete("/api/v1/organizations/members/" + bob.userId()).header("Authorization", alice.bearer()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", bob.bearer()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(bob.refreshCookie()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(json(post("/api/v1/auth/login"), Map.of("email", "bob@acme.com", "password", PASSWORD)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/organizations/members").header("Authorization", alice.bearer()))
                .andExpect(jsonPath("$.length()").value(1));
    }

    // --- Tenant isolation (PRD section 39) ------------------------------------------------

    @Test
    void adminCannotTouchMembersOfAnotherOrganization() throws Exception {
        Session alice = register("Alice", "alice@acme.com", "Acme");
        Session bob = register("Bob", "bob@globex.com", "Globex");
        Session globexMember = inviteAndAccept(bob, "Gina", "gina@globex.com", "MEMBER");

        mockMvc.perform(delete("/api/v1/organizations/members/" + globexMember.userId()).header("Authorization", alice.bearer()))
                .andExpect(status().isNotFound());
        mockMvc.perform(json(patch("/api/v1/organizations/members/" + bob.userId()), Map.of("role", "MEMBER"))
                        .header("Authorization", alice.bearer()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/organizations/members").header("Authorization", alice.bearer()))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].email").value("alice@acme.com"));
        mockMvc.perform(get("/api/v1/organizations/current").header("Authorization", alice.bearer()))
                .andExpect(jsonPath("$.name").value("Acme"));
    }

    @Test
    void adminCannotRevokeAnotherOrganizationsInvitation() throws Exception {
        Session alice = register("Alice", "alice@acme.com", "Acme");
        Session bob = register("Bob", "bob@globex.com", "Globex");
        String globexInvitation = invite(bob, "gina@globex.com", "MEMBER");

        mockMvc.perform(delete("/api/v1/organizations/invitations/" + globexInvitation).header("Authorization", alice.bearer()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/organizations/invitations").header("Authorization", alice.bearer()))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void unknownMemberIdIsNotFound() throws Exception {
        Session alice = register("Alice", "alice@acme.com", "Acme");

        mockMvc.perform(delete("/api/v1/organizations/members/" + UUID.randomUUID()).header("Authorization", alice.bearer()))
                .andExpect(status().isNotFound());
    }

    private String invite(Session admin, String email, String role) throws Exception {
        return body(mockMvc.perform(json(post("/api/v1/organizations/members/invite"), Map.of("email", email, "role", role))
                        .header("Authorization", admin.bearer()))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asString();
    }
}
