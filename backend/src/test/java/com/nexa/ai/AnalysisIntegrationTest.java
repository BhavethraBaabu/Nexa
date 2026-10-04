package com.nexa.ai;

import com.nexa.ai.llm.LlmException;
import com.nexa.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** End-to-end analysis through the API with a scripted model (PRD sections 10-14, 35, 46). */
class AnalysisIntegrationTest extends IntegrationTest {

    /** The PRD's end-to-end demo transcript (section 68). Meeting date Saturday 2026-10-03. */
    private static final String TRANSCRIPT = """
            John: I will implement Redis caching for the payment service by Friday.
            Sarah: I'll review it.
            Alice: We decided to use PostgreSQL for the new service.
            Mike: I'll document the deployment process.
            Alice: Someone should update the runbook soon.
            Sarah: The migration could delay the release.
            Mike: Who owns the production migration?
            """;

    private static final String MODEL_OUTPUT = """
            {
              "summary": "The team planned caching work and chose PostgreSQL.",
              "keyPoints": ["Redis caching for payments", "PostgreSQL selected"],
              "actionItems": [
                {"title": "Implement Redis caching", "description": "Add caching to the payment service.",
                 "ownerName": "John", "deadlineText": "by Friday", "deadline": "2026-10-09", "priority": "HIGH",
                 "confidence": 0.94, "evidence": "I will implement Redis caching for the payment service by Friday"},
                {"title": "Review Redis implementation", "description": "Review John's change.",
                 "ownerName": "Sarah", "deadlineText": null, "deadline": null, "priority": "MEDIUM",
                 "confidence": 0.91, "evidence": "I'll review it."},
                {"title": "Document deployment process", "description": "Write deployment docs.",
                 "ownerName": "Mike", "deadlineText": null, "deadline": null, "priority": "MEDIUM",
                 "confidence": 0.9, "evidence": "I'll document the deployment process."},
                {"title": "Update the runbook", "description": "Refresh the runbook.",
                 "ownerName": null, "deadlineText": "soon", "deadline": null, "priority": "LOW",
                 "confidence": 0.7, "evidence": "Someone should update the runbook soon."}
              ],
              "decisions": [
                {"decision": "Use PostgreSQL for the new service", "context": "Primary database", "confidence": 0.97,
                 "evidence": "We decided to use PostgreSQL for the new service."}
              ],
              "risks": [
                {"description": "Migration could delay the release", "severity": "HIGH", "confidence": 0.89,
                 "evidence": "The migration could delay the release."}
              ],
              "unresolvedQuestions": [
                {"question": "Who owns the production migration?", "confidence": 0.95,
                 "evidence": "Who owns the production migration?"}
              ]
            }
            """;

    @Autowired
    private AiProperties aiProperties;

    @Test
    void analysisTurnsTranscriptIntoResolvedStructuredWork() throws Exception {
        Session alice = register("Alice Admin", "alice@acme.com", "Acme");
        inviteAndAccept(alice, "John Smith", "john@acme.com", "MEMBER");
        inviteAndAccept(alice, "Sarah Lee", "sarah@acme.com", "MEMBER");
        String meetingId = createMeeting(alice);
        llm.respondWith(MODEL_OUTPUT);

        mockMvc.perform(post("/api/v1/meetings/" + meetingId + "/analyze").header("Authorization", alice.bearer()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.promptVersion").value("meeting-extraction/v1"));

        mockMvc.perform(get("/api/v1/meetings/" + meetingId).header("Authorization", alice.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.analysis.latestRun.status").value("COMPLETED"))
                .andExpect(jsonPath("$.analysis.latestRun.model").value("fake-model"))
                .andExpect(jsonPath("$.analysis.summary").value("The team planned caching work and chose PostgreSQL."))
                .andExpect(jsonPath("$.analysis.keyPoints.length()").value(2))
                // John is a member: resolved, with Friday resolved against the meeting date.
                .andExpect(jsonPath("$.analysis.actionItems[0].owner.name").value("John Smith"))
                .andExpect(jsonPath("$.analysis.actionItems[0].ownerStatus").value("RESOLVED"))
                .andExpect(jsonPath("$.analysis.actionItems[0].deadline").value("2026-10-09"))
                .andExpect(jsonPath("$.analysis.actionItems[0].deadlineStatus").value("RESOLVED"))
                .andExpect(jsonPath("$.analysis.actionItems[0].confidenceLevel").value("HIGH"))
                .andExpect(jsonPath("$.analysis.actionItems[0].status").value("SUGGESTED"))
                .andExpect(jsonPath("$.analysis.actionItems[1].owner.name").value("Sarah Lee"))
                // Mike isn't a member: kept as a name, never mapped to someone else.
                .andExpect(jsonPath("$.analysis.actionItems[2].owner").isEmpty())
                .andExpect(jsonPath("$.analysis.actionItems[2].ownerName").value("Mike"))
                .andExpect(jsonPath("$.analysis.actionItems[2].ownerStatus").value("UNRESOLVED"))
                // "Someone ... soon": no owner, deadline flagged for review with no invented date.
                .andExpect(jsonPath("$.analysis.actionItems[3].ownerStatus").value("UNASSIGNED"))
                .andExpect(jsonPath("$.analysis.actionItems[3].deadline").isEmpty())
                .andExpect(jsonPath("$.analysis.actionItems[3].deadlineStatus").value("NEEDS_REVIEW"))
                .andExpect(jsonPath("$.analysis.actionItems[3].confidenceLevel").value("MEDIUM"))
                .andExpect(jsonPath("$.analysis.decisions[0].decision").value("Use PostgreSQL for the new service"))
                .andExpect(jsonPath("$.analysis.risks[0].severity").value("HIGH"))
                .andExpect(jsonPath("$.analysis.questions[0].status").value("UNRESOLVED"));

        assertThat(llm.lastUserPrompt())
                .contains("Meeting date: Saturday, October 3, 2026 (2026-10-03)")
                .contains("Participants: John, Sarah, Alice, Mike")
                .contains("<transcript>\nJohn: I will implement Redis caching");
        assertThat(llm.lastSystemPrompt()).contains("Never infer an owner");
        Integer audits = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM audit_logs WHERE action IN ('MEETING_ANALYSIS_REQUESTED', 'MEETING_ANALYZED')", Integer.class);
        assertThat(audits).isEqualTo(2);
        Integer tokens = jdbcTemplate.queryForObject("SELECT input_tokens FROM meeting_analyses", Integer.class);
        assertThat(tokens).isEqualTo(1200);
    }

    @Test
    void dashboardCountsAnalyzedWork() throws Exception {
        Session alice = register("Alice", "alice@acme.com", "Acme");
        String meetingId = createMeeting(alice);
        llm.respondWith(MODEL_OUTPUT);
        mockMvc.perform(post("/api/v1/meetings/" + meetingId + "/analyze").header("Authorization", alice.bearer()));

        mockMvc.perform(get("/api/v1/dashboard").header("Authorization", alice.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalMeetings").value(1))
                .andExpect(jsonPath("$.actionItems").value(4))
                .andExpect(jsonPath("$.recentMeetings[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.recentDecisions[0].decision").value("Use PostgreSQL for the new service"));
    }

    @Test
    void providerFailureIsRecordedAndRetryable() throws Exception {
        Session alice = register("Alice", "alice@acme.com", "Acme");
        String meetingId = createMeeting(alice);
        llm.failWith(LlmException.Code.RATE_LIMITED);

        mockMvc.perform(post("/api/v1/meetings/" + meetingId + "/analyze").header("Authorization", alice.bearer()))
                .andExpect(status().isAccepted());

        mockMvc.perform(get("/api/v1/meetings/" + meetingId).header("Authorization", alice.bearer()))
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.transcript").value(TRANSCRIPT.strip()))
                .andExpect(jsonPath("$.analysis.latestRun.status").value("FAILED"))
                .andExpect(jsonPath("$.analysis.latestRun.errorCode").value("RATE_LIMITED"))
                .andExpect(jsonPath("$.analysis.latestRun.errorMessage").value("The AI provider is busy right now. Please try again in a minute."))
                .andExpect(jsonPath("$.analysis.latestRun.retryable").value(true))
                .andExpect(jsonPath("$.analysis.actionItems.length()").value(0));

        // Retry succeeds.
        llm.respondWith(MODEL_OUTPUT);
        mockMvc.perform(post("/api/v1/meetings/" + meetingId + "/analyze").header("Authorization", alice.bearer()))
                .andExpect(status().isAccepted());
        mockMvc.perform(get("/api/v1/meetings/" + meetingId + "/analysis").header("Authorization", alice.bearer()))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void failureKeepsPreviousResults() throws Exception {
        Session alice = register("Alice", "alice@acme.com", "Acme");
        String meetingId = createMeeting(alice);
        llm.respondWith(MODEL_OUTPUT).failWith(LlmException.Code.UNAVAILABLE);
        mockMvc.perform(post("/api/v1/meetings/" + meetingId + "/analyze").header("Authorization", alice.bearer()));
        mockMvc.perform(post("/api/v1/meetings/" + meetingId + "/analyze").header("Authorization", alice.bearer()));

        mockMvc.perform(get("/api/v1/meetings/" + meetingId).header("Authorization", alice.bearer()))
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.analysis.summary").value("The team planned caching work and chose PostgreSQL."))
                .andExpect(jsonPath("$.analysis.actionItems.length()").value(4));
    }

    @Test
    void unreadableModelOutputFailsSafely() throws Exception {
        Session alice = register("Alice", "alice@acme.com", "Acme");
        String meetingId = createMeeting(alice);
        llm.respondWith("{\"oops\": true}");

        mockMvc.perform(post("/api/v1/meetings/" + meetingId + "/analyze").header("Authorization", alice.bearer()));

        mockMvc.perform(get("/api/v1/meetings/" + meetingId + "/analysis").header("Authorization", alice.bearer()))
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.errorCode").value("INVALID_RESPONSE"));
    }

    @Test
    void missingApiKeyIsReportedAsNotRetryable() throws Exception {
        Session alice = register("Alice", "alice@acme.com", "Acme");
        String meetingId = createMeeting(alice);
        llm.failWith(LlmException.Code.NOT_CONFIGURED);

        mockMvc.perform(post("/api/v1/meetings/" + meetingId + "/analyze").header("Authorization", alice.bearer()));

        mockMvc.perform(get("/api/v1/meetings/" + meetingId + "/analysis").header("Authorization", alice.bearer()))
                .andExpect(jsonPath("$.errorCode").value("NOT_CONFIGURED"))
                .andExpect(jsonPath("$.retryable").value(false));
    }

    @Test
    void cannotStartASecondAnalysisWhileOneIsRunning() throws Exception {
        Session alice = register("Alice", "alice@acme.com", "Acme");
        String meetingId = createMeeting(alice);
        jdbcTemplate.update("UPDATE meetings SET status = 'PROCESSING'");

        mockMvc.perform(post("/api/v1/meetings/" + meetingId + "/analyze").header("Authorization", alice.bearer()))
                .andExpect(status().isConflict());
        mockMvc.perform(json(patch("/api/v1/meetings/" + meetingId), Map.of("transcript", "New text"))
                        .header("Authorization", alice.bearer()))
                .andExpect(status().isConflict());
        assertThat(llm.calls()).isZero();
    }

    @Test
    void reanalysisReplacesSuggestionsAndTranscriptEditsClearThem() throws Exception {
        Session alice = register("Alice", "alice@acme.com", "Acme");
        String meetingId = createMeeting(alice);
        llm.respondWith(MODEL_OUTPUT).respondWith(MODEL_OUTPUT);
        mockMvc.perform(post("/api/v1/meetings/" + meetingId + "/analyze").header("Authorization", alice.bearer()));
        mockMvc.perform(post("/api/v1/meetings/" + meetingId + "/analyze").header("Authorization", alice.bearer()));

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM tasks", Integer.class)).isEqualTo(4);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM meeting_analyses", Integer.class)).isEqualTo(2);

        mockMvc.perform(json(patch("/api/v1/meetings/" + meetingId), Map.of("transcript", "A different meeting entirely."))
                        .header("Authorization", alice.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UPLOADED"))
                .andExpect(jsonPath("$.analysis.summary").isEmpty())
                .andExpect(jsonPath("$.analysis.actionItems.length()").value(0));
    }

    @Test
    void staleAnalysesAreFailedSoTheyCanBeRetried() throws Exception {
        Session alice = register("Alice", "alice@acme.com", "Acme");
        String meetingId = createMeeting(alice);
        jdbcTemplate.update("""
                INSERT INTO meeting_analyses (id, organization_id, meeting_id, status, requested_by, prompt_version, created_at)
                VALUES (gen_random_uuid(), ?, ?::uuid, 'PROCESSING', ?, 'meeting-extraction/v1', now() - interval '1 hour')
                """, alice.organizationId(), meetingId, alice.userId());
        jdbcTemplate.update("UPDATE meetings SET status = 'PROCESSING'");

        recovery().failStaleAnalyses();

        mockMvc.perform(get("/api/v1/meetings/" + meetingId).header("Authorization", alice.bearer()))
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.analysis.latestRun.errorCode").value("TIMED_OUT"))
                .andExpect(jsonPath("$.analysis.latestRun.retryable").value(true));
    }

    @Autowired
    private org.springframework.context.ApplicationContext context;

    private com.nexa.ai.analysis.AnalysisRecoveryAccess recovery() {
        return new com.nexa.ai.analysis.AnalysisRecoveryAccess(context);
    }

    private String createMeeting(Session session) throws Exception {
        return body(mockMvc.perform(json(post("/api/v1/meetings"), Map.of(
                        "title", "Payment Architecture Review", "meetingDate", "2026-10-03",
                        "participants", java.util.List.of("John", "Sarah", "Alice", "Mike"), "transcript", TRANSCRIPT))
                        .header("Authorization", session.bearer()))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asString();
    }
}
