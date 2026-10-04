package com.nexa.ai.evaluation;

import com.nexa.ai.extraction.MeetingIntelligence;
import com.nexa.ai.evaluation.EvaluationScorer.Expected;
import com.nexa.ai.evaluation.EvaluationScorer.ExpectedAction;
import com.nexa.risk.Severity;
import com.nexa.task.DeadlineStatus;
import com.nexa.task.OwnerStatus;
import com.nexa.task.Priority;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class EvaluationScorerTest {

    private static final LocalDate FRIDAY = LocalDate.of(2026, 10, 9);

    @Test
    void scoresMatchesOwnersDeadlinesAndTraps() {
        MeetingIntelligence actual = new MeetingIntelligence("s", List.of(),
                List.of(action("Add Redis caching to the payment service", "John Smith", FRIDAY),
                        action("Review caching change", null, null),
                        action("Order team lunch", "Mike", null)),
                List.of(new MeetingIntelligence.DecisionItem("Use Redis for sessions", null, BigDecimal.ONE, null)),
                List.of(new MeetingIntelligence.RiskItem("Migration may delay the release", Severity.HIGH, BigDecimal.ONE, null)),
                List.of());
        Expected expected = new Expected(
                List.of(new ExpectedAction("Implement Redis caching for payments", "John", FRIDAY),
                        new ExpectedAction("Review the Redis caching implementation", "Sarah", null)),
                List.of("Use PostgreSQL"),
                List.of("Data migration may delay the release"),
                List.of("Redis"));

        EvaluationScorer.Score score = EvaluationScorer.score(actual, expected);

        assertThat(score.actions().truePositives()).isEqualTo(2);
        assertThat(score.actions().precision()).isCloseTo(2 / 3.0, within(1e-9));
        assertThat(score.actions().recall()).isEqualTo(1.0);
        assertThat(score.ownersCorrect()).isEqualTo(1); // John matches; null != Sarah.
        assertThat(score.deadlinesCorrect()).isEqualTo(2);
        assertThat(score.decisions().truePositives()).isZero();
        assertThat(score.risks().f1()).isEqualTo(1.0);
        assertThat(score.decisionTraps()).isEqualTo(1);
    }

    @Test
    void emptyExpectationsWithEmptyOutputArePerfect() {
        MeetingIntelligence nothing = new MeetingIntelligence("s", List.of(), List.of(), List.of(), List.of(), List.of());
        EvaluationScorer.Score score = EvaluationScorer.score(nothing, new Expected(List.of(), List.of(), List.of(), List.of()));
        assertThat(score.actions().f1()).isEqualTo(1.0);
        assertThat(score.ownerAccuracy()).isEqualTo(1.0);
    }

    @Test
    void inventedItemsLowerPrecision() {
        MeetingIntelligence invented = new MeetingIntelligence("s", List.of(), List.of(action("Plan a hike", "Raj", null)),
                List.of(), List.of(), List.of());
        EvaluationScorer.Score score = EvaluationScorer.score(invented, new Expected(List.of(), List.of(), List.of(), List.of()));
        assertThat(score.actions().precision()).isZero();
    }

    @Test
    void matchingIsOneToOne() {
        assertThat(EvaluationScorer.match(List.of("fix alert routing", "fix the alert routing now"), List.of("Fix alert routing")))
                .hasSize(1);
    }

    private static MeetingIntelligence.ActionItem action(String title, String owner, LocalDate deadline) {
        return new MeetingIntelligence.ActionItem(title, null, null, owner, owner == null ? OwnerStatus.UNASSIGNED : OwnerStatus.UNRESOLVED,
                Priority.MEDIUM, deadline, null, deadline == null ? DeadlineStatus.NONE : DeadlineStatus.RESOLVED, BigDecimal.ONE, null);
    }
}
