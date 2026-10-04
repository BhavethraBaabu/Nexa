package com.nexa.ai;

import com.nexa.ai.resolution.DeadlineResolver;
import com.nexa.ai.resolution.DeadlineResolver.Resolved;
import com.nexa.task.DeadlineStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class DeadlineResolverTest {

    /** Saturday, the PRD's example meeting date. */
    private static final LocalDate SATURDAY_OCT_3 = LocalDate.of(2026, 10, 3);
    private static final LocalDate WEDNESDAY_OCT_7 = LocalDate.of(2026, 10, 7);

    private final DeadlineResolver resolver = new DeadlineResolver();

    @Test
    void prdExampleFridayResolvesToOctoberNinth() {
        assertThat(resolver.resolve("by Friday", null, SATURDAY_OCT_3))
                .isEqualTo(new Resolved(LocalDate.of(2026, 10, 9), DeadlineStatus.RESOLVED));
    }

    @ParameterizedTest(name = "\"{0}\" said on Wed Oct 7 -> {1}")
    @CsvSource({
            "Friday,                2026-10-09",
            "this Friday,           2026-10-09",
            "Wednesday,             2026-10-14",
            "Monday,                2026-10-12",
            "tomorrow,              2026-10-08",
            "day after tomorrow,    2026-10-09",
            "end of day,            2026-10-07",
            "EOD,                   2026-10-07",
            "today,                 2026-10-07",
            "end of the week,       2026-10-09",
            "EOW,                   2026-10-09",
            "end of month,          2026-10-31",
            "in 3 days,             2026-10-10",
            "within two weeks,      2026-10-21",
            "in a week,             2026-10-14",
            "2026-11-02,            2026-11-02",
            "October 20,            2026-10-20",
            "by Oct 20th,           2026-10-20",
            "20 October,            2026-10-20",
            "the 20th of October,   2026-10-20",
            "Nov 3 2027,            2027-11-03",
    })
    void resolvesUnambiguousPhrases(String phrase, LocalDate expected) {
        assertThat(resolver.resolve(phrase, null, WEDNESDAY_OCT_7))
                .isEqualTo(new Resolved(expected, DeadlineStatus.RESOLVED));
    }

    @Test
    void endOfWeekOnAWeekendMeansTheFollowingFriday() {
        assertThat(resolver.resolve("end of week", null, SATURDAY_OCT_3).deadline()).isEqualTo(LocalDate.of(2026, 10, 9));
    }

    @Test
    void monthWithoutYearRollsIntoNextYearWhenAlreadyPast() {
        assertThat(resolver.resolve("January 5", null, LocalDate.of(2026, 12, 15)).deadline())
                .isEqualTo(LocalDate.of(2027, 1, 5));
    }

    @Test
    void nextWeekdayIsFlaggedForReview() {
        Resolved resolved = resolver.resolve("next Friday", null, WEDNESDAY_OCT_7);
        assertThat(resolved.status()).isEqualTo(DeadlineStatus.NEEDS_REVIEW);
        assertThat(resolved.deadline()).isEqualTo(LocalDate.of(2026, 10, 16));
    }

    @ParameterizedTest
    @CsvSource({"soon", "ASAP", "let's get this done soon", "next week", "next sprint", "at some point"})
    void vaguePhrasesNeedReviewWithoutAnInventedDate(String phrase) {
        assertThat(resolver.resolve(phrase, LocalDate.of(2026, 10, 14), WEDNESDAY_OCT_7))
                .isEqualTo(new Resolved(null, DeadlineStatus.NEEDS_REVIEW));
    }

    @Test
    void unknownPhraseFallsBackToModelSuggestionForReview() {
        assertThat(resolver.resolve("before the board meeting", LocalDate.of(2026, 10, 15), WEDNESDAY_OCT_7))
                .isEqualTo(new Resolved(LocalDate.of(2026, 10, 15), DeadlineStatus.NEEDS_REVIEW));
    }

    @Test
    void modelSuggestionInThePastIsDiscarded() {
        assertThat(resolver.resolve("before the board meeting", LocalDate.of(2026, 9, 1), WEDNESDAY_OCT_7))
                .isEqualTo(new Resolved(null, DeadlineStatus.NEEDS_REVIEW));
    }

    @Test
    void noPhraseMeansNoDeadlineEvenIfTheModelGuessedOne() {
        assertThat(resolver.resolve(null, LocalDate.of(2026, 10, 9), WEDNESDAY_OCT_7)).isEqualTo(new Resolved(null, DeadlineStatus.NONE));
        assertThat(resolver.resolve("  ", null, WEDNESDAY_OCT_7)).isEqualTo(new Resolved(null, DeadlineStatus.NONE));
    }

    @Test
    void invalidCalendarDateIsNotResolved() {
        assertThat(resolver.resolve("February 30", null, WEDNESDAY_OCT_7).status()).isEqualTo(DeadlineStatus.NEEDS_REVIEW);
    }
}
