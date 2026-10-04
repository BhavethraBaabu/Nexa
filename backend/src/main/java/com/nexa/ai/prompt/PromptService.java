package com.nexa.ai.prompt;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Versioned prompts (PRD section 32). The version is stored with every analysis so results
 * can be traced to the prompt that produced them and compared in evaluation runs.
 */
@Service
public class PromptService {

    public static final String EXTRACTION_VERSION = "meeting-extraction/v1";

    private static final DateTimeFormatter MEETING_DATE = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.ENGLISH);

    private final String extractionSystemPrompt;

    public PromptService() {
        this.extractionSystemPrompt = load("prompts/" + EXTRACTION_VERSION + "-system.md");
    }

    public String extractionSystemPrompt() {
        return extractionSystemPrompt;
    }

    public String extractionUserPrompt(String title, LocalDate meetingDate, List<String> participants, String transcript) {
        String people = participants.isEmpty() ? "Not listed" : String.join(", ", participants);
        return """
                Meeting title: %s
                Meeting date: %s (%s)
                Participants: %s

                <transcript>
                %s
                </transcript>
                """.formatted(title, meetingDate.format(MEETING_DATE), meetingDate, people, transcript);
    }

    private static String load(String path) {
        try {
            return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Missing prompt resource " + path, e);
        }
    }
}
