package com.nexa.ai;

import com.nexa.ai.extraction.ExtractionValidator;
import com.nexa.ai.extraction.MeetingIntelligence;
import com.nexa.ai.extraction.RawExtraction;
import com.nexa.ai.llm.LlmException;
import com.nexa.ai.resolution.DeadlineResolver;
import com.nexa.risk.Severity;
import com.nexa.task.DeadlineStatus;
import com.nexa.task.OwnerStatus;
import com.nexa.task.Priority;
import com.nexa.user.MemberNameResolver;
import com.nexa.user.Role;
import com.nexa.user.User;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExtractionValidatorTest {

    private static final LocalDate MEETING_DATE = LocalDate.of(2026, 10, 3);
    private static final String TRANSCRIPT = """
            John: I'll implement Redis caching for the payment service by Friday.
            Sarah: I can review it once it's ready.
            Mike: Someone should update the deployment docs.
            Sarah: We decided to use PostgreSQL for the new service.
            John: The database migration may delay the release.
            Mike: Who owns the production migration?
            """;

    private final User john = User.create(UUID.randomUUID(), "John Smith", "john@acme.com", "{noop}x", Role.MEMBER, Instant.now());
    private final ExtractionValidator validator = new ExtractionValidator(
            JsonMapper.builder().build(), new MemberNameResolver(), new DeadlineResolver());

    @Test
    void resolvesOwnersAndDeadlinesAndVerifiesEvidence() {
        RawExtraction raw = new RawExtraction("Summary.", List.of("Caching", "Database"),
                List.of(
                        action("Implement Redis caching", "John", "by Friday", "2026-10-09", "HIGH", 0.95,
                                "I'll implement Redis caching for the payment service by Friday"),
                        action("Update deployment docs", null, null, null, "MEDIUM", 0.8,
                                "Someone should update the deployment docs"),
                        action("Review implementation", "Sarah", null, null, "MEDIUM", 0.9,
                                "I can review it once it's ready")),
                List.of(new RawExtraction.DecisionItem("Use PostgreSQL", "New service", 0.97,
                        "We decided to use PostgreSQL for the new service")),
                List.of(new RawExtraction.RiskItem("Migration may delay release", "HIGH", 0.89,
                        "The database migration may delay the release")),
                List.of(new RawExtraction.QuestionItem("Who owns the production migration?", 0.9,
                        "Who owns the production migration?")));

        MeetingIntelligence result = validator.validate(raw, TRANSCRIPT, MEETING_DATE, List.of(john));

        MeetingIntelligence.ActionItem caching = result.actionItems().get(0);
        assertThat(caching.ownerId()).isEqualTo(john.getId());
        assertThat(caching.ownerStatus()).isEqualTo(OwnerStatus.RESOLVED);
        assertThat(caching.deadline()).isEqualTo(LocalDate.of(2026, 10, 9));
        assertThat(caching.deadlineStatus()).isEqualTo(DeadlineStatus.RESOLVED);
        assertThat(caching.priority()).isEqualTo(Priority.HIGH);
        assertThat(caching.confidence()).isEqualByComparingTo("0.95");

        MeetingIntelligence.ActionItem docs = result.actionItems().get(1);
        assertThat(docs.ownerId()).isNull();
        assertThat(docs.ownerStatus()).isEqualTo(OwnerStatus.UNASSIGNED);
        assertThat(docs.deadlineStatus()).isEqualTo(DeadlineStatus.NONE);

        MeetingIntelligence.ActionItem review = result.actionItems().get(2);
        assertThat(review.ownerName()).isEqualTo("Sarah");
        assertThat(review.ownerStatus()).isEqualTo(OwnerStatus.UNRESOLVED); // Sarah isn't a member: never invented.
        assertThat(review.ownerId()).isNull();

        assertThat(result.decisions()).singleElement().extracting(MeetingIntelligence.DecisionItem::confidence)
                .isEqualTo(new BigDecimal("0.97"));
        assertThat(result.risks()).singleElement().extracting(MeetingIntelligence.RiskItem::severity).isEqualTo(Severity.HIGH);
        assertThat(result.questions()).hasSize(1);
        assertThat(result.keyPoints()).containsExactly("Caching", "Database");
    }

    @Test
    void evidenceNotInTranscriptCapsConfidenceAtLow() {
        RawExtraction raw = new RawExtraction("Summary.", List.of(),
                List.of(action("Rewrite billing in Rust", "John", null, null, "HIGH", 0.99,
                        "Let's rewrite billing in Rust next quarter")),
                List.of(), List.of(), List.of());

        assertThat(validator.validate(raw, TRANSCRIPT, MEETING_DATE, List.of(john)).actionItems().getFirst().confidence())
                .isEqualByComparingTo("0.50");
    }

    @Test
    void evidenceMatchingIgnoresCaseQuotesAndSpacing() {
        RawExtraction raw = new RawExtraction("Summary.", List.of(),
                List.of(action("Implement caching", "John", null, null, "HIGH", 0.92,
                        "i’ll   IMPLEMENT redis caching")),
                List.of(), List.of(), List.of());

        assertThat(validator.validate(raw, TRANSCRIPT, MEETING_DATE, List.of(john)).actionItems().getFirst().confidence())
                .isEqualByComparingTo("0.92");
    }

    @Test
    void dropsEmptyAndDuplicateItemsAndClampsValues() {
        RawExtraction raw = new RawExtraction("Summary.", List.of("  ", "Point", "Point"),
                List.of(
                        action("  ", "John", null, null, "HIGH", 0.9, "x"),
                        action("Implement Redis caching", "John", null, null, "URGENT", 1.7,
                                "I'll implement Redis caching for the payment service by Friday"),
                        action("implement redis caching!", "John", null, null, "LOW", 0.9, "dup")),
                List.of(new RawExtraction.DecisionItem("Use PostgreSQL", null, -3.0,
                        "We decided to use PostgreSQL for the new service")),
                List.of(new RawExtraction.RiskItem("Risk", "CATASTROPHIC", null, null)),
                null);

        MeetingIntelligence result = validator.validate(raw, TRANSCRIPT, MEETING_DATE, List.of(john));

        assertThat(result.actionItems()).singleElement().satisfies(a -> {
            assertThat(a.priority()).isEqualTo(Priority.MEDIUM);
            assertThat(a.confidence()).isEqualByComparingTo("1.00");
        });
        assertThat(result.decisions().getFirst().confidence()).isEqualByComparingTo("0.00");
        assertThat(result.risks().getFirst().severity()).isEqualTo(Severity.MEDIUM);
        assertThat(result.risks().getFirst().confidence()).isEqualByComparingTo("0.50");
        assertThat(result.questions()).isEmpty();
        assertThat(result.keyPoints()).containsExactly("Point");
    }

    @Test
    void parseRejectsMalformedOrSummarylessOutput() {
        assertThatThrownBy(() -> validator.parse("not json"))
                .isInstanceOfSatisfying(LlmException.class, e -> assertThat(e.code()).isEqualTo(LlmException.Code.INVALID_RESPONSE));
        assertThatThrownBy(() -> validator.parse("{\"summary\": \"  \", \"actionItems\": []}"))
                .isInstanceOfSatisfying(LlmException.class, e -> assertThat(e.code()).isEqualTo(LlmException.Code.INVALID_RESPONSE));
    }

    @Test
    void parseToleratesMissingListsAndUnknownFields() {
        RawExtraction raw = validator.parse("{\"summary\": \"Short.\", \"extra\": 1}");
        assertThat(validator.validate(raw, TRANSCRIPT, MEETING_DATE, List.of()).actionItems()).isEmpty();
    }

    private static RawExtraction.ActionItem action(String title, String owner, String deadlineText, String deadline,
                                                   String priority, Double confidence, String evidence) {
        return new RawExtraction.ActionItem(title, title + " description", owner, deadlineText, deadline, priority, confidence, evidence);
    }
}
