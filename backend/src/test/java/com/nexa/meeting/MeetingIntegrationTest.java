package com.nexa.meeting;

import com.nexa.support.IntegrationTest;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MeetingIntegrationTest extends IntegrationTest {

    private static final String TRANSCRIPT = "John: I'll implement Redis caching by Friday.\nSarah: I'll review it.";

    @Test
    void createMeetingResolvesParticipantsToMembers() throws Exception {
        Session alice = register("Alice Admin", "alice@acme.com", "Acme");
        inviteAndAccept(alice, "John Smith", "john@acme.com", "MEMBER");

        mockMvc.perform(json(post("/api/v1/meetings"), meeting("Payment Architecture Review", List.of("John", "Priya", "John")))
                        .header("Authorization", alice.bearer()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Payment Architecture Review"))
                .andExpect(jsonPath("$.meetingDate").value("2026-10-03"))
                .andExpect(jsonPath("$.status").value("UPLOADED"))
                .andExpect(jsonPath("$.participants.length()").value(2))
                .andExpect(jsonPath("$.participants[0].name").value("John"))
                .andExpect(jsonPath("$.participants[0].userId").isNotEmpty())
                .andExpect(jsonPath("$.participants[1].name").value("Priya"))
                .andExpect(jsonPath("$.participants[1].userId").isEmpty())
                .andExpect(jsonPath("$.createdBy.name").value("Alice Admin"))
                .andExpect(jsonPath("$.canEdit").value(true))
                .andExpect(jsonPath("$.analysis").isEmpty());
    }

    @Test
    void createValidatesInput() throws Exception {
        Session alice = register("Alice", "alice@acme.com", "Acme");

        mockMvc.perform(json(post("/api/v1/meetings"), Map.of("title", "", "transcript", " "))
                        .header("Authorization", alice.bearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.violations.length()").value(3));
    }

    @Test
    void overlongTranscriptIsRejectedNotTruncated() throws Exception {
        Session alice = register("Alice", "alice@acme.com", "Acme");
        Map<String, Object> body = new HashMap<>(meeting("Huge", List.of()));
        body.put("transcript", "a".repeat(300_001));

        mockMvc.perform(json(post("/api/v1/meetings"), body).header("Authorization", alice.bearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("too long")));
    }

    @Test
    void listIsPaginatedNewestFirst() throws Exception {
        Session alice = register("Alice", "alice@acme.com", "Acme");
        create(alice, "Older", "2026-09-01");
        create(alice, "Newest", "2026-10-02");
        create(alice, "Middle", "2026-09-15");

        mockMvc.perform(get("/api/v1/meetings?page=0&size=2").header("Authorization", alice.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].title").value("Newest"))
                .andExpect(jsonPath("$.content[1].title").value("Middle"))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void meetingsAreInvisibleToOtherOrganizations() throws Exception {
        Session alice = register("Alice", "alice@acme.com", "Acme");
        Session bob = register("Bob", "bob@globex.com", "Globex");
        String id = create(alice, "Acme secret plans", "2026-10-03");

        mockMvc.perform(get("/api/v1/meetings/" + id).header("Authorization", bob.bearer())).andExpect(status().isNotFound());
        mockMvc.perform(json(patch("/api/v1/meetings/" + id), Map.of("title", "pwned")).header("Authorization", bob.bearer()))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/meetings/" + id).header("Authorization", bob.bearer())).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/meetings/" + id + "/analyze").header("Authorization", bob.bearer()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/meetings").header("Authorization", bob.bearer()))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void membersCanViewButOnlyCreatorsAndManagersCanEdit() throws Exception {
        Session alice = register("Alice", "alice@acme.com", "Acme");
        Session member = inviteAndAccept(alice, "John", "john@acme.com", "MEMBER");
        Session manager = inviteAndAccept(alice, "Mia", "mia@acme.com", "MANAGER");
        String alicesMeeting = create(alice, "Alice's meeting", "2026-10-03");
        String johnsMeeting = create(member, "John's meeting", "2026-10-03");

        mockMvc.perform(get("/api/v1/meetings/" + alicesMeeting).header("Authorization", member.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canEdit").value(false));
        mockMvc.perform(json(patch("/api/v1/meetings/" + alicesMeeting), Map.of("title", "x")).header("Authorization", member.bearer()))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/meetings/" + alicesMeeting).header("Authorization", member.bearer()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/meetings/" + alicesMeeting + "/analyze").header("Authorization", member.bearer()))
                .andExpect(status().isForbidden());

        mockMvc.perform(json(patch("/api/v1/meetings/" + johnsMeeting), Map.of("title", "John renamed")).header("Authorization", member.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("John renamed"));
        mockMvc.perform(json(patch("/api/v1/meetings/" + johnsMeeting), Map.of("durationMinutes", 45)).header("Authorization", manager.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.durationMinutes").value(45))
                .andExpect(jsonPath("$.title").value("John renamed"));
    }

    @Test
    void deleteRemovesTheMeeting() throws Exception {
        Session alice = register("Alice", "alice@acme.com", "Acme");
        String id = create(alice, "Temp", "2026-10-03");

        mockMvc.perform(delete("/api/v1/meetings/" + id).header("Authorization", alice.bearer())).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/meetings/" + id).header("Authorization", alice.bearer())).andExpect(status().isNotFound());
    }

    // --- Transcript files (PRD section 9) -------------------------------------------------

    @Test
    void extractsTextFromTxtPdfAndDocx() throws Exception {
        Session alice = register("Alice", "alice@acme.com", "Acme");

        upload(alice, new MockMultipartFile("file", "notes.txt", "text/plain", "﻿John: hello from txt".getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("John: hello from txt"))
                .andExpect(jsonPath("$.characters").value(20));
        upload(alice, new MockMultipartFile("file", "notes.pdf", "application/pdf", pdf("Sarah: hello from pdf")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("Sarah: hello from pdf"));
        upload(alice, new MockMultipartFile("file", "notes.docx", "application/octet-stream", docx("Mike: hello from docx")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("Mike: hello from docx"));
    }

    @Test
    void rejectsUnsupportedOrDisguisedFiles() throws Exception {
        Session alice = register("Alice", "alice@acme.com", "Acme");

        upload(alice, new MockMultipartFile("file", "image.png", "image/png", new byte[]{1, 2, 3}))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Unsupported file type")));
        upload(alice, new MockMultipartFile("file", "fake.pdf", "application/pdf", "not a pdf".getBytes()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("valid PDF")));
        upload(alice, new MockMultipartFile("file", "latin1.txt", "text/plain", new byte[]{(byte) 0xE9, 'a'}))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("UTF-8")));
        upload(alice, new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0]))
                .andExpect(status().isBadRequest());
    }

    @Test
    void transcriptUploadRequiresAuthentication() throws Exception {
        mockMvc.perform(multipart("/api/v1/meetings/transcript-file")
                        .file(new MockMultipartFile("file", "a.txt", "text/plain", "x".getBytes())))
                .andExpect(status().isUnauthorized());
    }

    private Map<String, Object> meeting(String title, List<String> participants) {
        return Map.of("title", title, "meetingDate", "2026-10-03", "durationMinutes", 30,
                "participants", participants, "transcript", TRANSCRIPT);
    }

    private String create(Session session, String title, String date) throws Exception {
        return body(mockMvc.perform(json(post("/api/v1/meetings"),
                                Map.of("title", title, "meetingDate", date, "transcript", TRANSCRIPT))
                        .header("Authorization", session.bearer()))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asString();
    }

    private org.springframework.test.web.servlet.ResultActions upload(Session session, MockMultipartFile file) throws Exception {
        return mockMvc.perform(multipart("/api/v1/meetings/transcript-file").file(file).header("Authorization", session.bearer()));
    }

    private static byte[] pdf(String text) throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(72, 700);
                content.showText(text);
                content.endText();
            }
            document.save(out);
            return out.toByteArray();
        }
    }

    private static byte[] docx(String text) throws Exception {
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText(text);
            document.write(out);
            return out.toByteArray();
        }
    }
}
