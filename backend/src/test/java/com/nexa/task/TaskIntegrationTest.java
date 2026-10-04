package com.nexa.task;

import com.nexa.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Phase 4: the task dashboard and editing AI suggestions (PRD sections 15 and 26). */
class TaskIntegrationTest extends IntegrationTest {

    private static final String MODEL_OUTPUT = """
            {"summary": "Planning.", "keyPoints": [],
             "actionItems": [
               {"title": "Implement Redis caching", "description": "Cache payment queries.", "ownerName": "John",
                "deadlineText": "by Friday", "deadline": "2026-10-09", "priority": "HIGH", "confidence": 0.94,
                "evidence": "I will implement Redis caching by Friday."},
               {"title": "Update the runbook", "description": null, "ownerName": "Priya",
                "deadlineText": "soon", "deadline": null, "priority": "LOW", "confidence": 0.6,
                "evidence": "Priya should update the runbook soon."}],
             "decisions": [], "risks": [], "unresolvedQuestions": []}
            """;

    private Session alice;
    private Session john;
    private String cachingTask;
    private String runbookTask;

    @BeforeEach
    void setUp() throws Exception {
        alice = register("Alice Admin", "alice@acme.com", "Acme");
        john = inviteAndAccept(alice, "John Smith", "john@acme.com", "MEMBER");
        String meetingId = body(mockMvc.perform(json(post("/api/v1/meetings"), Map.of("title", "Sprint Planning",
                        "meetingDate", "2026-10-03",
                        "transcript", "John: I will implement Redis caching by Friday.\nAlice: Priya should update the runbook soon."))
                .header("Authorization", alice.bearer())).andReturn()).get("id").asString();
        llm.respondWith(MODEL_OUTPUT);
        mockMvc.perform(post("/api/v1/meetings/" + meetingId + "/analyze").header("Authorization", alice.bearer()));
        JsonNode all = body(mockMvc.perform(get("/api/v1/tasks").header("Authorization", alice.bearer())).andReturn());
        for (JsonNode t : all.get("content")) {
            if (t.get("title").asString().contains("Redis")) cachingTask = t.get("id").asString();
            else runbookTask = t.get("id").asString();
        }
    }

    @Test
    void listShowsTaskOwnerDeadlineSourceAndStatus() throws Exception {
        mockMvc.perform(get("/api/v1/tasks").header("Authorization", alice.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                // Dated tasks first.
                .andExpect(jsonPath("$.content[0].title").value("Implement Redis caching"))
                .andExpect(jsonPath("$.content[0].owner.name").value("John Smith"))
                .andExpect(jsonPath("$.content[0].deadline").value("2026-10-09"))
                .andExpect(jsonPath("$.content[0].meeting.title").value("Sprint Planning"))
                .andExpect(jsonPath("$.content[0].status").value("SUGGESTED"))
                .andExpect(jsonPath("$.content[0].pendingActions").value(2))
                .andExpect(jsonPath("$.content[1].ownerStatus").value("UNRESOLVED"))
                .andExpect(jsonPath("$.content[1].deadlineStatus").value("NEEDS_REVIEW"));
    }

    @Test
    void filtersMatchThePrd() throws Exception {
        assertCount("MINE", john, 1);
        assertCount("MINE", alice, 0);
        assertCount("TEAM", alice, 1);
        assertCount("SUGGESTED", alice, 2);
        assertCount("PENDING_APPROVAL", alice, 2);
        assertCount("COMPLETED", alice, 0);

        assertCount("OVERDUE", alice, 0);
        mockMvc.perform(json(patch("/api/v1/tasks/" + cachingTask), Map.of("deadline", "2020-01-01")).header("Authorization", alice.bearer()))
                .andExpect(jsonPath("$.overdue").value(true));
        assertCount("OVERDUE", alice, 1);

        mockMvc.perform(json(patch("/api/v1/tasks/" + cachingTask), Map.of("status", "DONE")).header("Authorization", alice.bearer()));
        assertCount("COMPLETED", alice, 1);
        assertCount("OVERDUE", alice, 0);
    }

    @Test
    void editorsCanFixWhatTheAiGotWrong() throws Exception {
        Map<String, Object> edit = new HashMap<>();
        edit.put("title", "Update the on-call runbook");
        edit.put("description", "Include the new escalation policy.");
        edit.put("ownerId", john.userId().toString());
        edit.put("priority", "MEDIUM");
        edit.put("deadline", "2026-10-15");
        edit.put("status", "OPEN");

        mockMvc.perform(json(patch("/api/v1/tasks/" + runbookTask), edit).header("Authorization", alice.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Update the on-call runbook"))
                .andExpect(jsonPath("$.owner.name").value("John Smith"))
                .andExpect(jsonPath("$.ownerStatus").value("RESOLVED"))
                .andExpect(jsonPath("$.deadline").value("2026-10-15"))
                .andExpect(jsonPath("$.deadlineStatus").value("RESOLVED"))
                .andExpect(jsonPath("$.priority").value("MEDIUM"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.editedBy.name").value("Alice Admin"));

        mockMvc.perform(json(patch("/api/v1/tasks/" + runbookTask), Map.of("clearOwner", true, "clearDeadline", true))
                        .header("Authorization", alice.bearer()))
                .andExpect(jsonPath("$.owner").isEmpty())
                .andExpect(jsonPath("$.ownerStatus").value("UNASSIGNED"))
                .andExpect(jsonPath("$.deadline").isEmpty())
                .andExpect(jsonPath("$.deadlineStatus").value("NONE"));

        Integer audits = jdbcTemplate.queryForObject("SELECT count(*) FROM audit_logs WHERE action = 'TASK_UPDATED'", Integer.class);
        assertThat(audits).isEqualTo(2);
    }

    @Test
    void ownersCanOnlyChangeTheStatusOfTheirOwnTasks() throws Exception {
        mockMvc.perform(json(patch("/api/v1/tasks/" + cachingTask), Map.of("status", "IN_PROGRESS")).header("Authorization", john.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
        mockMvc.perform(json(patch("/api/v1/tasks/" + cachingTask), Map.of("title", "Something else")).header("Authorization", john.bearer()))
                .andExpect(status().isForbidden());
        mockMvc.perform(json(patch("/api/v1/tasks/" + runbookTask), Map.of("status", "DONE")).header("Authorization", john.bearer()))
                .andExpect(status().isForbidden());
    }

    @Test
    void ownerMustBeAMemberOfTheSameOrganization() throws Exception {
        Session bob = register("Bob", "bob@globex.com", "Globex");

        mockMvc.perform(json(patch("/api/v1/tasks/" + cachingTask), Map.of("ownerId", bob.userId().toString()))
                        .header("Authorization", alice.bearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("The selected owner isn't a member of your organization"));
    }

    @Test
    void tasksAreInvisibleToOtherOrganizations() throws Exception {
        Session bob = register("Bob", "bob@globex.com", "Globex");

        mockMvc.perform(get("/api/v1/tasks/" + cachingTask).header("Authorization", bob.bearer())).andExpect(status().isNotFound());
        mockMvc.perform(json(patch("/api/v1/tasks/" + cachingTask), Map.of("status", "DONE")).header("Authorization", bob.bearer()))
                .andExpect(status().isNotFound());
        assertCount("ALL", bob, 0);
    }

    @Test
    void acceptedTasksSurviveReanalysis() throws Exception {
        mockMvc.perform(json(patch("/api/v1/tasks/" + cachingTask), Map.of("status", "OPEN")).header("Authorization", alice.bearer()));
        String meetingId = body(mockMvc.perform(get("/api/v1/tasks/" + cachingTask).header("Authorization", alice.bearer())).andReturn())
                .get("meeting").get("id").asString();
        llm.respondWith(MODEL_OUTPUT);
        mockMvc.perform(post("/api/v1/meetings/" + meetingId + "/analyze").header("Authorization", alice.bearer()));

        mockMvc.perform(get("/api/v1/tasks/" + cachingTask).header("Authorization", alice.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("OPEN"));
        mockMvc.perform(get("/api/v1/tasks/" + runbookTask).header("Authorization", alice.bearer()))
                .andExpect(status().isNotFound()); // the untouched suggestion was replaced
    }

    private void assertCount(String filter, Session session, int expected) throws Exception {
        mockMvc.perform(get("/api/v1/tasks").param("filter", filter).header("Authorization", session.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(expected));
    }
}
