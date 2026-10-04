package com.nexa.ai.evaluation;

import com.nexa.ai.AiProperties;
import com.nexa.ai.extraction.ExtractionSchema;
import com.nexa.ai.extraction.ExtractionValidator;
import com.nexa.ai.extraction.MeetingIntelligence;
import com.nexa.ai.extraction.TranscriptPreprocessor;
import com.nexa.ai.llm.AnthropicLlmClient;
import com.nexa.ai.llm.StructuredCompletion;
import com.nexa.ai.prompt.PromptService;
import com.nexa.ai.resolution.DeadlineResolver;
import com.nexa.user.MemberNameResolver;
import com.nexa.user.Role;
import com.nexa.user.User;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Runs the real model over /evaluation and reports extraction quality (PRD sections 48-49).
 * Calls the paid API, so it is excluded from normal builds: run with
 * {@code LLM_API_KEY=... ./mvnw test -Pevaluation}.
 */
@Tag("evaluation")
@EnabledIfEnvironmentVariable(named = "LLM_API_KEY", matches = ".+")
class MeetingExtractionEvaluation {

    private static final Path DATASET = Path.of("..", "evaluation");

    private final JsonMapper json = JsonMapper.builder().build();
    private final PromptService prompts = new PromptService();
    private final TranscriptPreprocessor preprocessor = new TranscriptPreprocessor();
    private final ExtractionValidator validator = new ExtractionValidator(json, new MemberNameResolver(), new DeadlineResolver());

    @Test
    void evaluateDataset() throws Exception {
        AiProperties properties = new AiProperties(System.getenv("LLM_API_KEY"),
                env("LLM_BASE_URL", "https://api.anthropic.com"), env("LLM_MODEL", "claude-opus-5-5"),
                env("LLM_EFFORT", "medium"), 16000, Duration.ofMinutes(2), 300_000, Duration.ofMinutes(15));
        AnthropicLlmClient llm = new AnthropicLlmClient(properties);

        EvaluationScorer.Score total = EvaluationScorer.Score.empty();
        List<Map<String, Object>> perMeeting = new ArrayList<>();
        long inputTokens = 0;
        long outputTokens = 0;
        System.out.printf("%nModel %s, effort %s, prompt %s%n%n", properties.model(), properties.effort(), PromptService.EXTRACTION_VERSION);
        System.out.printf("%-14s %8s %8s %8s %7s %8s %6s %7s%n", "meeting", "act F1", "dec F1", "risk F1", "owner", "deadline", "traps", "ms");

        try (Stream<Path> files = Files.list(DATASET.resolve("meetings"))) {
            for (Path transcriptFile : files.sorted().toList()) {
                String name = transcriptFile.getFileName().toString().replace(".txt", "");
                JsonNode exp = json.readTree(DATASET.resolve("expected").resolve(name + ".json").toFile());
                LocalDate meetingDate = LocalDate.parse(exp.get("meetingDate").asString());
                UUID org = UUID.randomUUID();
                List<User> members = strings(exp.get("members")).stream()
                        .map(n -> User.create(org, n, n.toLowerCase().replace(' ', '.') + "@example.com", "{noop}x", Role.MEMBER, Instant.now()))
                        .toList();
                String transcript = preprocessor.clean(Files.readString(transcriptFile));

                long started = System.nanoTime();
                StructuredCompletion completion = llm.completeStructured(prompts.extractionSystemPrompt(),
                        prompts.extractionUserPrompt(exp.get("title").asString(), meetingDate, strings(exp.get("participants")), transcript),
                        ExtractionSchema.schema());
                long ms = (System.nanoTime() - started) / 1_000_000;
                inputTokens += completion.inputTokens();
                outputTokens += completion.outputTokens();
                MeetingIntelligence actual = validator.validate(validator.parse(completion.json()), transcript, meetingDate, members);

                EvaluationScorer.Score score = EvaluationScorer.score(actual, expected(exp));
                total = total.plus(score);
                System.out.printf("%-14s %8.2f %8.2f %8.2f %7.2f %8.2f %6d %7d%n", name, score.actions().f1(), score.decisions().f1(),
                        score.risks().f1(), score.ownerAccuracy(), score.deadlineAccuracy(), score.decisionTraps(), ms);
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("meeting", name);
                row.put("score", score);
                row.put("durationMs", ms);
                row.put("output", json.readTree(completion.json()));
                perMeeting.add(row);
            }
        }

        System.out.printf("%n%-14s %8.2f %8.2f %8.2f %7.2f %8.2f %6d%n", "OVERALL", total.actions().f1(), total.decisions().f1(),
                total.risks().f1(), total.ownerAccuracy(), total.deadlineAccuracy(), total.decisionTraps());
        System.out.printf("Action items: precision %.2f, recall %.2f. Tokens: %,d in / %,d out%n%n",
                total.actions().precision(), total.actions().recall(), inputTokens, outputTokens);

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("model", properties.model());
        report.put("effort", properties.effort());
        report.put("promptVersion", PromptService.EXTRACTION_VERSION);
        report.put("ranAt", Instant.now().toString());
        report.put("overall", Map.of(
                "actionPrecision", total.actions().precision(), "actionRecall", total.actions().recall(),
                "actionF1", total.actions().f1(), "decisionF1", total.decisions().f1(), "riskF1", total.risks().f1(),
                "ownerAccuracy", total.ownerAccuracy(), "deadlineAccuracy", total.deadlineAccuracy(),
                "decisionTraps", total.decisionTraps(), "inputTokens", inputTokens, "outputTokens", outputTokens));
        report.put("meetings", perMeeting);
        Path results = DATASET.resolve("results");
        Files.createDirectories(results);
        json.writerWithDefaultPrettyPrinter().writeValue(results.resolve("latest.json").toFile(), report);
    }

    private EvaluationScorer.Expected expected(JsonNode exp) {
        List<EvaluationScorer.ExpectedAction> actions = new ArrayList<>();
        for (JsonNode a : exp.get("actionItems")) {
            actions.add(new EvaluationScorer.ExpectedAction(a.get("title").asString(),
                    a.get("owner").isNull() ? null : a.get("owner").asString(),
                    a.get("deadline").isNull() ? null : LocalDate.parse(a.get("deadline").asString())));
        }
        return new EvaluationScorer.Expected(actions, strings(exp.get("decisions")), strings(exp.get("risks")),
                strings(exp.get("mustNotDecide")));
    }

    private static List<String> strings(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(n -> values.add(n.asString()));
        return values;
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
