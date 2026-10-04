package com.nexa.action;

import com.nexa.support.IntegrationTest;
import com.nexa.support.StubProviderServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phases 5-7 end to end: analysis proposes actions, a person approves them, and Nexa executes them
 * against (stub) Jira and Slack - with OAuth, idempotency, retries and tenant isolation.
 */
class ActionsAndIntegrationsIntegrationTest extends IntegrationTest {

    private static final StubProviderServer PROVIDERS = new StubProviderServer();

    @DynamicPropertySource
    static void providerUrls(DynamicPropertyRegistry registry) {
        registry.add("nexa.integrations.jira.auth-base-url", PROVIDERS::url);
        registry.add("nexa.integrations.jira.api-base-url", PROVIDERS::url);
        registry.add("nexa.integrations.slack.base-url", PROVIDERS::url);
    }

    @AfterAll
    static void stopProviders() {
        PROVIDERS.stop();
    }

    private static final String TRANSCRIPT = """
            John: I will implement Redis caching for the payment service by Friday.
            Alice: Someone should update the runbook.
            Alice: We decided to use PostgreSQL for the new service.
            """;

    private static final String MODEL_OUTPUT = """
            {"summary": "Caching and database decisions.", "keyPoints": ["Caching"],
             "actionItems": [
               {"title": "Implement Redis caching", "description": "Cache payment queries.", "ownerName": "John",
                "deadlineText": "by Friday", "deadline": "2026-10-09", "priority": "HIGH", "confidence": 0.94,
                "evidence": "I will implement Redis caching for the payment service by Friday."},
               {"title": "Update the runbook", "description": "Refresh it.", "ownerName": null,
                "deadlineText": null, "deadline": null, "priority": "LOW", "confidence": 0.8,
                "evidence": "Someone should update the runbook."}],
             "decisions": [{"decision": "Use PostgreSQL for the new service", "context": "New service", "confidence": 0.97,
                "evidence": "We decided to use PostgreSQL for the new service."}],
             "risks": [], "unresolvedQuestions": []}
            """;

    @Autowired
    private ActionExecutor executor;

    private Session alice;
    private Session john;
    private String meetingId;

    @BeforeEach
    void setUp() throws Exception {
        PROVIDERS.reset();
        alice = register("Alice Admin", "alice@acme.com", "Acme");
        john = inviteAndAccept(alice, "John Smith", "john@acme.com", "MEMBER");
        meetingId = body(mockMvc.perform(json(post("/api/v1/meetings"), Map.of("title", "Payment Architecture Review",
                        "meetingDate", "2026-10-03", "transcript", TRANSCRIPT)).header("Authorization", alice.bearer()))
                .andExpect(status().isCreated()).andReturn()).get("id").asString();
        llm.respondWith(MODEL_OUTPUT);
        mockMvc.perform(post("/api/v1/meetings/" + meetingId + "/analyze").header("Authorization", alice.bearer()))
                .andExpect(status().isAccepted());
    }

    // --- Phase 5: suggestions and review ------------------------------------------------------

    @Test
    void analysisProposesJiraAndSlackPerTaskPlusOneEmailDraft() throws Exception {
        List<JsonNode> actions = actionsForMeeting();
        assertThat(actions).hasSize(5).allSatisfy(a -> assertThat(a.get("status").asString()).isEqualTo("PENDING"));
        assertThat(actions).filteredOn(a -> a.get("type").asString().equals("CREATE_JIRA_ISSUE")).hasSize(2)
                .allSatisfy(a -> assertThat(a.get("blockedReason").asString()).isEqualTo("Connect Jira in Integrations first."));
        assertThat(actions).filteredOn(a -> a.get("type").asString().equals("SEND_SLACK_MESSAGE")).hasSize(2);
        assertThat(actions).filteredOn(a -> a.get("type").asString().equals("DRAFT_EMAIL")).singleElement()
                .satisfies(a -> assertThat(a.get("blockedReason").isNull()).isTrue());
        mockMvc.perform(get("/api/v1/actions/pending").header("Authorization", alice.bearer()))
                .andExpect(jsonPath("$.totalElements").value(5));
    }

    @Test
    void nothingIsSentWithoutApproval() throws Exception {
        connectAndConfigureJira();
        assertThat(PROVIDERS.requests("POST /ex/jira/cloud-1/rest/api/3/issue")).isEmpty();
    }

    @Test
    void approvalIsBlockedUntilTheIntegrationIsReady() throws Exception {
        UUID jiraAction = action("CREATE_JIRA_ISSUE", 0);

        mockMvc.perform(post("/api/v1/actions/" + jiraAction + "/approve").header("Authorization", alice.bearer()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message").value("Connect Jira in Integrations first."));

        connectJira();
        mockMvc.perform(post("/api/v1/actions/" + jiraAction + "/approve").header("Authorization", alice.bearer()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message").value(containsString("Choose a Jira project")));
    }

    @Test
    void emailDraftNeedsNoIntegrationAndFollowsThePrdTemplate() throws Exception {
        UUID email = action("DRAFT_EMAIL", 0);

        mockMvc.perform(post("/api/v1/actions/" + email + "/approve").header("Authorization", alice.bearer()))
                .andExpect(status().isOk());

        JsonNode done = actionById(email);
        assertThat(done.get("status").asString()).isEqualTo("COMPLETED");
        assertThat(done.get("result").asString())
                .startsWith("Subject: Payment Architecture Review — Action Items")
                .contains("• John — Implement Redis caching — Oct 9")
                .contains("• Unassigned — Update the runbook")
                .contains("Decision:\n• Use PostgreSQL for the new service");
    }

    @Test
    void rejectedActionsCannotBeApprovedLater() throws Exception {
        UUID email = action("DRAFT_EMAIL", 0);
        mockMvc.perform(post("/api/v1/actions/" + email + "/reject").header("Authorization", alice.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"));
        mockMvc.perform(post("/api/v1/actions/" + email + "/approve").header("Authorization", alice.bearer()))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void membersCanOnlyApproveActionsFromTheirOwnMeetings() throws Exception {
        UUID email = action("DRAFT_EMAIL", 0);
        mockMvc.perform(post("/api/v1/actions/" + email + "/approve").header("Authorization", john.bearer()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/meetings/" + meetingId + "/actions").header("Authorization", john.bearer()))
                .andExpect(jsonPath("$[0].canApprove").value(false));
    }

    @Test
    void actionsAreInvisibleToOtherOrganizations() throws Exception {
        Session bob = register("Bob", "bob@globex.com", "Globex");
        UUID email = action("DRAFT_EMAIL", 0);

        mockMvc.perform(post("/api/v1/actions/" + email + "/approve").header("Authorization", bob.bearer()))
                .andExpect(status().isNotFound());
        mockMvc.perform(json(post("/api/v1/actions/approve"), Map.of("ids", List.of(email))).header("Authorization", bob.bearer()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/actions/pending").header("Authorization", bob.bearer()))
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/api/v1/meetings/" + meetingId + "/actions").header("Authorization", bob.bearer()))
                .andExpect(status().isNotFound());
    }

    // --- Phase 6: Jira ----------------------------------------------------------------------

    @Test
    void jiraOAuthStoresEncryptedTokensAndNeverExposesThem() throws Exception {
        connectJira();

        String stored = jdbcTemplate.queryForObject("SELECT encrypted_access_token FROM integrations", String.class);
        assertThat(stored).doesNotContain("jira-access-1");
        MvcResult list = mockMvc.perform(get("/api/v1/integrations").header("Authorization", john.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.provider == 'JIRA')].connected").value(true))
                .andExpect(jsonPath("$[?(@.provider == 'JIRA')].workspaceName").value("Acme Jira"))
                .andReturn();
        assertThat(list.getResponse().getContentAsString()).doesNotContain("jira-access-1").doesNotContain("jira-refresh-1");

        PROVIDERS.requests("POST /oauth/token").forEach(r -> assertThat(r.body()).contains("\"code\":\"auth-code\""));
    }

    @Test
    void approvedTaskBecomesAJiraIssueExactlyOnce() throws Exception {
        connectAndConfigureJira();
        stubJiraIssueCreation("PAY-101");
        UUID jiraAction = action("CREATE_JIRA_ISSUE", 0);

        mockMvc.perform(post("/api/v1/actions/" + jiraAction + "/approve").header("Authorization", alice.bearer()))
                .andExpect(status().isOk());
        // Double click: no second issue (PRD section 52).
        mockMvc.perform(post("/api/v1/actions/" + jiraAction + "/approve").header("Authorization", alice.bearer()))
                .andExpect(status().isOk());

        assertThat(PROVIDERS.requests("POST /ex/jira/cloud-1/rest/api/3/issue")).hasSize(1);
        String issue = PROVIDERS.requests("POST /ex/jira/cloud-1/rest/api/3/issue").getFirst().body();
        assertThat(issue).contains("\"summary\":\"Implement Redis caching\"", "\"key\":\"PAY\"", "\"id\":\"10001\"",
                "\"duedate\":\"2026-10-09\"", "\"name\":\"High\"", "\"accountId\":\"acc-john\"", "\"nexa\"", "Source meeting: Payment Architecture Review");

        JsonNode done = actionById(jiraAction);
        assertThat(done.get("status").asString()).isEqualTo("COMPLETED");
        assertThat(done.get("external").get("id").asString()).isEqualTo("PAY-101");
        assertThat(done.get("external").get("url").asString()).isEqualTo("https://acme.atlassian.net/browse/PAY-101");
        assertThat(done.get("approvedBy").get("name").asString()).isEqualTo("Alice Admin");

        // The task was accepted and shows its Jira link.
        mockMvc.perform(get("/api/v1/tasks/" + done.get("task").get("id").asString()).header("Authorization", alice.bearer()))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.externalLinks[0].id").value("PAY-101"));
        Integer audits = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM audit_logs WHERE action IN ('ACTION_APPROVED', 'ACTION_COMPLETED')", Integer.class);
        assertThat(audits).isEqualTo(2);
    }

    @Test
    void anIssueCreatedByAnInterruptedAttemptIsReusedNotDuplicated() throws Exception {
        connectAndConfigureJira();
        PROVIDERS.on("POST /ex/jira/cloud-1/rest/api/3/search/jql", 200, "{\"issues\": [{\"key\": \"PAY-77\"}]}");
        UUID jiraAction = action("CREATE_JIRA_ISSUE", 0);

        mockMvc.perform(post("/api/v1/actions/" + jiraAction + "/approve").header("Authorization", alice.bearer()));

        assertThat(PROVIDERS.requests("POST /ex/jira/cloud-1/rest/api/3/issue")).isEmpty();
        assertThat(actionById(jiraAction).get("external").get("id").asString()).isEqualTo("PAY-77");
    }

    @Test
    void transientJiraFailuresRetryWithBackoffThenFailAndCanBeRetriedManually() throws Exception {
        connectAndConfigureJira();
        PROVIDERS.on("POST /ex/jira/cloud-1/rest/api/3/search/jql", 503, "{}");
        UUID jiraAction = action("CREATE_JIRA_ISSUE", 0);

        mockMvc.perform(post("/api/v1/actions/" + jiraAction + "/approve").header("Authorization", alice.bearer()));
        JsonNode first = actionById(jiraAction);
        assertThat(first.get("status").asString()).isEqualTo("APPROVED"); // waiting for automatic retry
        assertThat(first.get("attempts").asInt()).isEqualTo(1);
        assertThat(first.get("errorCode").asString()).isEqualTo("UNAVAILABLE");
        assertThat(first.get("nextAttemptAt").isNull()).isFalse();

        executor.dispatch(jiraAction); // what the scheduler does when the retry is due
        executor.dispatch(jiraAction);
        JsonNode failed = actionById(jiraAction);
        assertThat(failed.get("status").asString()).isEqualTo("FAILED");
        assertThat(failed.get("attempts").asInt()).isEqualTo(3);
        assertThat(failed.get("errorMessage").asString()).contains("temporarily unavailable");
        assertThat(failed.get("retryable").asBoolean()).isTrue();

        // Jira recovers; a person retries.
        PROVIDERS.reset();
        stubJiraIssueCreation("PAY-102");
        mockMvc.perform(post("/api/v1/actions/" + jiraAction + "/retry").header("Authorization", alice.bearer()))
                .andExpect(status().isOk());
        assertThat(actionById(jiraAction).get("status").asString()).isEqualTo("COMPLETED");
    }

    @Test
    void revokedJiraAccessFailsImmediatelyAndFlagsTheIntegration() throws Exception {
        connectAndConfigureJira();
        PROVIDERS.on("POST /ex/jira/cloud-1/rest/api/3/search/jql", 401, "{}");
        UUID jiraAction = action("CREATE_JIRA_ISSUE", 0);

        mockMvc.perform(post("/api/v1/actions/" + jiraAction + "/approve").header("Authorization", alice.bearer()));

        JsonNode failed = actionById(jiraAction);
        assertThat(failed.get("status").asString()).isEqualTo("FAILED");
        assertThat(failed.get("errorCode").asString()).isEqualTo("AUTH_FAILED");
        assertThat(failed.get("attempts").asInt()).isEqualTo(1);
        mockMvc.perform(get("/api/v1/integrations").header("Authorization", alice.bearer()))
                .andExpect(jsonPath("$[?(@.provider == 'JIRA')].status").value("ERROR"));
    }

    @Test
    void expiredJiraTokenIsRefreshedAndTheRotatedTokenStored() throws Exception {
        connectAndConfigureJira();
        jdbcTemplate.update("UPDATE integrations SET expires_at = now() - interval '1 minute'");
        String before = jdbcTemplate.queryForObject("SELECT encrypted_refresh_token FROM integrations", String.class);
        PROVIDERS.reset();
        PROVIDERS.on("POST /oauth/token", 200, "{\"access_token\": \"jira-access-2\", \"refresh_token\": \"jira-refresh-2\", \"expires_in\": 3600}");
        stubJiraIssueCreation("PAY-103");

        mockMvc.perform(post("/api/v1/actions/" + action("CREATE_JIRA_ISSUE", 0) + "/approve").header("Authorization", alice.bearer()));

        assertThat(PROVIDERS.requests("POST /oauth/token").getLast().body()).contains("\"grant_type\":\"refresh_token\"", "jira-refresh-1");
        assertThat(PROVIDERS.requests("POST /ex/jira/cloud-1/rest/api/3/issue").getFirst().header("Authorization")).isEqualTo("Bearer jira-access-2");
        assertThat(jdbcTemplate.queryForObject("SELECT encrypted_refresh_token FROM integrations", String.class)).isNotEqualTo(before);
    }

    @Test
    void tamperedOrMismatchedOAuthStateIsRejected() throws Exception {
        String slackState = stateFrom(mockMvc.perform(post("/api/v1/integrations/slack/connect").header("Authorization", alice.bearer()))
                .andReturn());

        mockMvc.perform(get("/api/v1/integrations/jira/callback").param("code", "x").param("state", slackState))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", containsString("result=error")));
        mockMvc.perform(get("/api/v1/integrations/jira/callback").param("code", "x").param("state", "forged.state.value"))
                .andExpect(header().string("Location", containsString("result=error")));
        mockMvc.perform(get("/api/v1/integrations/jira/callback").param("error", "access_denied"))
                .andExpect(header().string("Location", containsString("result=cancelled")));
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM integrations", Integer.class)).isZero();
    }

    @Test
    void onlyAdminsManageIntegrations() throws Exception {
        mockMvc.perform(post("/api/v1/integrations/jira/connect").header("Authorization", john.bearer())).andExpect(status().isForbidden());
        mockMvc.perform(json(put("/api/v1/integrations/slack/config"), Map.of("channelId", "C1")).header("Authorization", john.bearer()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/audit-logs").header("Authorization", john.bearer())).andExpect(status().isForbidden());
    }

    // --- Phase 7: Slack ---------------------------------------------------------------------

    @Test
    void approvedActionPostsToTheConfiguredSlackChannel() throws Exception {
        connectAndConfigureSlack();
        PROVIDERS.on("POST /api/chat.postMessage", 200, "{\"ok\": true, \"channel\": \"C1\", \"ts\": \"1700000000.000100\"}")
                .on("GET /api/chat.getPermalink", 200, "{\"ok\": true, \"permalink\": \"https://acme.slack.com/archives/C1/p1700000000000100\"}");
        UUID slackAction = action("SEND_SLACK_MESSAGE", 0);

        mockMvc.perform(post("/api/v1/actions/" + slackAction + "/approve").header("Authorization", alice.bearer()))
                .andExpect(status().isOk());

        StubProviderServer.Request message = PROVIDERS.requests("POST /api/chat.postMessage").getFirst();
        assertThat(message.header("Authorization")).isEqualTo("Bearer xoxb-test");
        assertThat(message.body()).contains("\"channel\":\"C1\"", "Nexa Meeting Action", "*Task:* Implement Redis caching",
                "*Owner:* John Smith", "*Due:* October 9", "*Priority:* High", "*Source:* Payment Architecture Review");
        JsonNode done = actionById(slackAction);
        assertThat(done.get("status").asString()).isEqualTo("COMPLETED");
        assertThat(done.get("external").get("id").asString()).isEqualTo("C1:1700000000.000100");
        assertThat(done.get("external").get("url").asString()).startsWith("https://acme.slack.com/archives/C1");
    }

    @Test
    void slackChannelProblemsAreExplainedAndNotRetriedBlindly() throws Exception {
        connectAndConfigureSlack();
        PROVIDERS.on("POST /api/chat.postMessage", 200, "{\"ok\": false, \"error\": \"channel_not_found\"}");
        UUID slackAction = action("SEND_SLACK_MESSAGE", 0);

        mockMvc.perform(post("/api/v1/actions/" + slackAction + "/approve").header("Authorization", alice.bearer()));

        JsonNode failed = actionById(slackAction);
        assertThat(failed.get("status").asString()).isEqualTo("FAILED");
        assertThat(failed.get("errorMessage").asString()).contains("channel no longer exists");
    }

    @Test
    void approveSelectedRunsEverythingAtOnce() throws Exception {
        connectAndConfigureJira();
        connectAndConfigureSlack();
        stubJiraIssueCreation("PAY-200");
        PROVIDERS.on("POST /api/chat.postMessage", 200, "{\"ok\": true, \"channel\": \"C1\", \"ts\": \"1.2\"}");
        List<String> ids = actionsForMeeting().stream().map(a -> a.get("id").asString()).toList();

        mockMvc.perform(json(post("/api/v1/actions/approve"), Map.of("ids", ids)).header("Authorization", alice.bearer()))
                .andExpect(status().isOk());

        assertThat(actionsForMeeting()).allSatisfy(a -> assertThat(a.get("status").asString()).isEqualTo("COMPLETED"));
        assertThat(PROVIDERS.requests("POST /ex/jira/cloud-1/rest/api/3/issue")).hasSize(2);
        assertThat(PROVIDERS.requests("POST /api/chat.postMessage")).hasSize(2);
        mockMvc.perform(get("/api/v1/dashboard").header("Authorization", alice.bearer()))
                .andExpect(jsonPath("$.pendingApprovals").value(0));
        mockMvc.perform(get("/api/v1/audit-logs").header("Authorization", alice.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].userName").isNotEmpty());
    }

    // --- Helpers ----------------------------------------------------------------------------

    private void connectJira() throws Exception {
        PROVIDERS.on("POST /oauth/token", 200, "{\"access_token\": \"jira-access-1\", \"refresh_token\": \"jira-refresh-1\", \"expires_in\": 3600}")
                .on("GET /oauth/token/accessible-resources", 200,
                        "[{\"id\": \"cloud-1\", \"name\": \"Acme Jira\", \"url\": \"https://acme.atlassian.net\"}]");
        MvcResult connect = mockMvc.perform(post("/api/v1/integrations/jira/connect").header("Authorization", alice.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authorizeUrl").value(containsString("audience=api.atlassian.com")))
                .andReturn();
        mockMvc.perform(get("/api/v1/integrations/jira/callback").param("code", "auth-code").param("state", stateFrom(connect)))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "http://localhost:3000/integrations?provider=jira&result=connected"));
    }

    private void connectAndConfigureJira() throws Exception {
        connectJira();
        PROVIDERS.on("GET /ex/jira/cloud-1/rest/api/3/project/search", 200, "{\"values\": [{\"key\": \"PAY\", \"name\": \"Payments\"}]}")
                .on("GET /ex/jira/cloud-1/rest/api/3/issue/createmeta/PAY/issuetypes", 200,
                        "{\"issueTypes\": [{\"id\": \"10001\", \"name\": \"Task\", \"subtask\": false}, {\"id\": \"10002\", \"name\": \"Sub-task\", \"subtask\": true}]}");
        mockMvc.perform(get("/api/v1/integrations/jira/projects").header("Authorization", alice.bearer()))
                .andExpect(jsonPath("$[0].key").value("PAY"));
        mockMvc.perform(get("/api/v1/integrations/jira/projects/PAY/issue-types").header("Authorization", alice.bearer()))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Task"));
        mockMvc.perform(json(put("/api/v1/integrations/jira/config"), Map.of("projectKey", "PAY", "projectName", "Payments",
                        "issueTypeId", "10001", "issueTypeName", "Task", "defaultPriority", "Medium"))
                        .header("Authorization", alice.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(true));
    }

    private void connectAndConfigureSlack() throws Exception {
        PROVIDERS.on("POST /api/oauth.v2.access", 200, "{\"ok\": true, \"access_token\": \"xoxb-test\", \"team\": {\"id\": \"T1\", \"name\": \"Acme\"}}")
                .on("GET /api/conversations.list", 200,
                        "{\"ok\": true, \"channels\": [{\"id\": \"C1\", \"name\": \"engineering\"}], \"response_metadata\": {\"next_cursor\": \"\"}}");
        MvcResult connect = mockMvc.perform(post("/api/v1/integrations/slack/connect").header("Authorization", alice.bearer()))
                .andExpect(jsonPath("$.authorizeUrl").value(containsString("scope=chat:write,chat:write.public,channels:read")))
                .andReturn();
        mockMvc.perform(get("/api/v1/integrations/slack/callback").param("code", "slack-code").param("state", stateFrom(connect)))
                .andExpect(header().string("Location", containsString("result=connected")));
        mockMvc.perform(get("/api/v1/integrations/slack/channels").header("Authorization", alice.bearer()))
                .andExpect(jsonPath("$[0].name").value("engineering"));
        mockMvc.perform(json(put("/api/v1/integrations/slack/config"), Map.of("channelId", "C1", "channelName", "engineering"))
                        .header("Authorization", alice.bearer()))
                .andExpect(jsonPath("$.configured").value(true));
    }

    private void stubJiraIssueCreation(String key) {
        PROVIDERS.on("POST /ex/jira/cloud-1/rest/api/3/search/jql", 200, "{\"issues\": []}")
                .on("GET /ex/jira/cloud-1/rest/api/3/user/search", 200,
                        "[{\"accountId\": \"acc-john\", \"emailAddress\": \"john@acme.com\", \"accountType\": \"atlassian\"}]")
                .on("POST /ex/jira/cloud-1/rest/api/3/issue", 201, "{\"id\": \"1\", \"key\": \"" + key + "\"}");
    }

    private String stateFrom(MvcResult connect) throws Exception {
        String url = body(connect).get("authorizeUrl").asString();
        String state = UriComponentsBuilder.fromUriString(url).build().getQueryParams().getFirst("state");
        return URLDecoder.decode(state, StandardCharsets.UTF_8);
    }

    private List<JsonNode> actionsForMeeting() throws Exception {
        JsonNode list = body(mockMvc.perform(get("/api/v1/meetings/" + meetingId + "/actions").header("Authorization", alice.bearer()))
                .andReturn());
        return java.util.stream.StreamSupport.stream(list.spliterator(), false).toList();
    }

    private UUID action(String type, int index) throws Exception {
        return UUID.fromString(actionsForMeeting().stream().filter(a -> a.get("type").asString().equals(type))
                .toList().get(index).get("id").asString());
    }

    private JsonNode actionById(UUID id) throws Exception {
        return actionsForMeeting().stream().filter(a -> a.get("id").asString().equals(id.toString())).findFirst().orElseThrow();
    }
}
