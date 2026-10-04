package com.nexa.ai.resolution;

import com.nexa.task.DeadlineStatus;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.time.format.DateTimeParseException;
import java.time.format.TextStyle;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves deadline phrases against the meeting date (PRD section 14). Resolution is
 * deterministic: the model reports the phrase it heard, and this class decides the date.
 * Phrases that are vague or open to interpretation are flagged NEEDS_REVIEW.
 */
@Component
public class DeadlineResolver {

    public record Resolved(LocalDate deadline, DeadlineStatus status) {

        static Resolved none() {
            return new Resolved(null, DeadlineStatus.NONE);
        }

        static Resolved exact(LocalDate date) {
            return new Resolved(date, DeadlineStatus.RESOLVED);
        }

        static Resolved review(LocalDate date) {
            return new Resolved(date, DeadlineStatus.NEEDS_REVIEW);
        }
    }

    private static final Map<String, Integer> NUMBER_WORDS = Map.ofEntries(
            Map.entry("a", 1), Map.entry("an", 1), Map.entry("one", 1), Map.entry("two", 2),
            Map.entry("three", 3), Map.entry("four", 4), Map.entry("five", 5), Map.entry("six", 6),
            Map.entry("seven", 7), Map.entry("eight", 8), Map.entry("nine", 9), Map.entry("ten", 10),
            Map.entry("couple of", 2));

    private static final Pattern ISO_DATE = Pattern.compile("\\b(\\d{4}-\\d{2}-\\d{2})\\b");
    private static final Pattern IN_N_UNITS = Pattern.compile(
            "\\b(?:in|within)\\s+(\\d+|a|an|one|two|three|four|five|six|seven|eight|nine|ten|couple of)\\s+(day|days|week|weeks)\\b");
    private static final Pattern MONTH_DAY = Pattern.compile(
            "\\b(jan|feb|mar|apr|may|jun|jul|aug|sep|sept|oct|nov|dec)[a-z]*\\.?\\s+(\\d{1,2})(?:st|nd|rd|th)?(?:,?\\s+(\\d{4}))?\\b");
    private static final Pattern DAY_MONTH = Pattern.compile(
            "\\b(\\d{1,2})(?:st|nd|rd|th)?\\s+(?:of\\s+)?(jan|feb|mar|apr|may|jun|jul|aug|sep|sept|oct|nov|dec)[a-z]*\\.?(?:,?\\s+(\\d{4}))?\\b");
    private static final Pattern VAGUE = Pattern.compile(
            "\\b(soon|asap|as soon as possible|shortly|eventually|later|at some point|when possible|next week|next month|next sprint|this quarter|next quarter)\\b");

    /**
     * @param deadlineText the phrase from the transcript, e.g. "by Friday"; null if none was mentioned
     * @param modelDeadline the model's own interpretation, used only as a reviewable suggestion
     *                      when the phrase cannot be resolved deterministically
     */
    public Resolved resolve(String deadlineText, LocalDate modelDeadline, LocalDate meetingDate) {
        if (deadlineText == null || deadlineText.isBlank()) {
            return Resolved.none();
        }
        String text = deadlineText.toLowerCase(Locale.ROOT).trim();

        Optional<Resolved> resolved = parse(text, meetingDate);
        if (resolved.isPresent()) {
            return resolved.get();
        }
        if (modelDeadline != null && !modelDeadline.isBefore(meetingDate)) {
            return Resolved.review(modelDeadline);
        }
        return Resolved.review(null);
    }

    private Optional<Resolved> parse(String text, LocalDate meetingDate) {
        if (VAGUE.matcher(text).find()) {
            return Optional.of(Resolved.review(null));
        }

        Matcher iso = ISO_DATE.matcher(text);
        if (iso.find()) {
            try {
                return Optional.of(Resolved.exact(LocalDate.parse(iso.group(1))));
            } catch (DateTimeParseException e) {
                return Optional.empty();
            }
        }

        Optional<LocalDate> calendar = monthDay(text, meetingDate);
        if (calendar.isPresent()) {
            return Optional.of(Resolved.exact(calendar.get()));
        }

        if (containsAny(text, "end of day", "eod", "today", "tonight", "this afternoon", "this evening")) {
            return Optional.of(Resolved.exact(meetingDate));
        }
        if (text.contains("day after tomorrow")) {
            return Optional.of(Resolved.exact(meetingDate.plusDays(2)));
        }
        if (text.contains("tomorrow")) {
            return Optional.of(Resolved.exact(meetingDate.plusDays(1)));
        }
        if (containsAny(text, "end of the week", "end of week", "eow", "end of this week")) {
            return Optional.of(Resolved.exact(upcoming(meetingDate, DayOfWeek.FRIDAY, true)));
        }
        if (containsAny(text, "end of the month", "end of month", "eom", "end of this month")) {
            return Optional.of(Resolved.exact(meetingDate.with(TemporalAdjusters.lastDayOfMonth())));
        }

        Matcher in = IN_N_UNITS.matcher(text);
        if (in.find()) {
            String amount = in.group(1);
            int n = amount.chars().allMatch(Character::isDigit) ? Integer.parseInt(amount) : NUMBER_WORDS.get(amount);
            boolean weeks = in.group(2).startsWith("week");
            return Optional.of(Resolved.exact(meetingDate.plusDays(weeks ? 7L * n : n)));
        }

        for (DayOfWeek day : DayOfWeek.values()) {
            String name = day.getDisplayName(TextStyle.FULL, Locale.ENGLISH).toLowerCase(Locale.ROOT);
            if (!Pattern.compile("\\b" + name + "\\b").matcher(text).find()) {
                continue;
            }
            if (Pattern.compile("\\bnext\\s+" + name + "\\b").matcher(text).find()) {
                // "Next Friday" means different things to different people: flag it.
                LocalDate nextWeekMonday = meetingDate.with(TemporalAdjusters.next(DayOfWeek.MONDAY));
                return Optional.of(Resolved.review(nextWeekMonday.with(TemporalAdjusters.nextOrSame(day))));
            }
            return Optional.of(Resolved.exact(upcoming(meetingDate, day, false)));
        }
        return Optional.empty();
    }

    /**
     * The next {@code day} strictly after the meeting date. For "end of week" the meeting day
     * itself counts if it is that day.
     */
    private static LocalDate upcoming(LocalDate meetingDate, DayOfWeek day, boolean includeSameDay) {
        return includeSameDay
                ? meetingDate.with(TemporalAdjusters.nextOrSame(day))
                : meetingDate.with(TemporalAdjusters.next(day));
    }

    private static Optional<LocalDate> monthDay(String text, LocalDate meetingDate) {
        Matcher m = MONTH_DAY.matcher(text);
        if (m.find()) {
            return toDate(m.group(1), m.group(2), m.group(3), meetingDate);
        }
        m = DAY_MONTH.matcher(text);
        if (m.find()) {
            return toDate(m.group(2), m.group(1), m.group(3), meetingDate);
        }
        return Optional.empty();
    }

    private static Optional<LocalDate> toDate(String monthText, String dayText, String yearText, LocalDate meetingDate) {
        Month month = parseMonth(monthText);
        int day = Integer.parseInt(dayText);
        try {
            if (yearText != null) {
                return Optional.of(LocalDate.of(Integer.parseInt(yearText), month, day));
            }
            LocalDate candidate = LocalDate.of(meetingDate.getYear(), month, day);
            // "Jan 5" said in a December meeting means next year.
            return Optional.of(candidate.isBefore(meetingDate) ? candidate.plusYears(1) : candidate);
        } catch (java.time.DateTimeException e) {
            return Optional.empty();
        }
    }

    private static Month parseMonth(String prefix) {
        for (Month month : Month.values()) {
            if (month.getDisplayName(TextStyle.FULL, Locale.ENGLISH).toLowerCase(Locale.ROOT).startsWith(prefix.substring(0, 3))) {
                return month;
            }
        }
        throw new IllegalArgumentException("Unknown month: " + prefix);
    }

    private static boolean containsAny(String text, String... phrases) {
        for (String phrase : phrases) {
            if (Pattern.compile("\\b" + Pattern.quote(phrase) + "\\b").matcher(text).find()) {
                return true;
            }
        }
        return false;
    }
}
