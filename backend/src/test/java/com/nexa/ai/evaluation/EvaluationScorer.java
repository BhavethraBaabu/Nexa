package com.nexa.ai.evaluation;

import com.nexa.ai.extraction.MeetingIntelligence;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Scores one extraction against expectations (PRD section 48). Items are paired by word overlap,
 * so wording differences ("Implement Redis caching" vs "Add Redis cache to payments") still match.
 */
public final class EvaluationScorer {

    static final double MATCH_THRESHOLD = 0.3;
    private static final Set<String> STOP_WORDS = Set.of("a", "an", "the", "to", "for", "of", "and", "or", "in", "on",
            "by", "with", "it", "is", "be", "we", "our", "new", "use", "set", "up");

    public record ExpectedAction(String title, String owner, LocalDate deadline) {
    }

    public record Expected(List<ExpectedAction> actionItems, List<String> decisions, List<String> risks,
                           List<String> mustNotDecide) {
    }

    public record Counts(int truePositives, int extracted, int expected) {

        Counts plus(Counts other) {
            return new Counts(truePositives + other.truePositives, extracted + other.extracted, expected + other.expected);
        }

        public double precision() {
            return extracted == 0 ? (expected == 0 ? 1.0 : 0.0) : (double) truePositives / extracted;
        }

        public double recall() {
            return expected == 0 ? 1.0 : (double) truePositives / expected;
        }

        public double f1() {
            double p = precision();
            double r = recall();
            return p + r == 0 ? 0.0 : 2 * p * r / (p + r);
        }
    }

    public record Score(Counts actions, Counts decisions, Counts risks, int ownersCorrect, int deadlinesCorrect,
                        int matchedActions, int decisionTraps) {

        static Score empty() {
            Counts zero = new Counts(0, 0, 0);
            return new Score(zero, zero, zero, 0, 0, 0, 0);
        }

        public Score plus(Score o) {
            return new Score(actions.plus(o.actions), decisions.plus(o.decisions), risks.plus(o.risks),
                    ownersCorrect + o.ownersCorrect, deadlinesCorrect + o.deadlinesCorrect,
                    matchedActions + o.matchedActions, decisionTraps + o.decisionTraps);
        }

        public double ownerAccuracy() {
            return matchedActions == 0 ? 1.0 : (double) ownersCorrect / matchedActions;
        }

        public double deadlineAccuracy() {
            return matchedActions == 0 ? 1.0 : (double) deadlinesCorrect / matchedActions;
        }
    }

    private EvaluationScorer() {
    }

    public static Score score(MeetingIntelligence actual, Expected expected) {
        List<String> actualTitles = actual.actionItems().stream().map(MeetingIntelligence.ActionItem::title).toList();
        List<int[]> pairs = match(actualTitles, expected.actionItems().stream().map(ExpectedAction::title).toList());

        int ownersCorrect = 0;
        int deadlinesCorrect = 0;
        for (int[] pair : pairs) {
            MeetingIntelligence.ActionItem a = actual.actionItems().get(pair[0]);
            ExpectedAction e = expected.actionItems().get(pair[1]);
            if (sameOwner(a.ownerName(), e.owner())) {
                ownersCorrect++;
            }
            if (Objects.equals(a.deadline(), e.deadline())) {
                deadlinesCorrect++;
            }
        }

        List<String> decisions = actual.decisions().stream().map(MeetingIntelligence.DecisionItem::decision).toList();
        int traps = (int) decisions.stream()
                .filter(d -> expected.mustNotDecide().stream().anyMatch(t -> d.toLowerCase(Locale.ROOT).contains(t.toLowerCase(Locale.ROOT))))
                .count();

        return new Score(
                new Counts(pairs.size(), actualTitles.size(), expected.actionItems().size()),
                counts(decisions, expected.decisions()),
                counts(actual.risks().stream().map(MeetingIntelligence.RiskItem::description).toList(), expected.risks()),
                ownersCorrect, deadlinesCorrect, pairs.size(), traps);
    }

    private static Counts counts(List<String> actual, List<String> expected) {
        return new Counts(match(actual, expected).size(), actual.size(), expected.size());
    }

    /** Greedy one-to-one pairing by descending similarity. Returns [actualIndex, expectedIndex] pairs. */
    static List<int[]> match(List<String> actual, List<String> expected) {
        record Candidate(int a, int e, double similarity) {
        }
        List<Candidate> candidates = new ArrayList<>();
        for (int a = 0; a < actual.size(); a++) {
            for (int e = 0; e < expected.size(); e++) {
                double s = similarity(actual.get(a), expected.get(e));
                if (s >= MATCH_THRESHOLD) {
                    candidates.add(new Candidate(a, e, s));
                }
            }
        }
        candidates.sort((x, y) -> Double.compare(y.similarity, x.similarity));
        Set<Integer> usedA = new HashSet<>();
        Set<Integer> usedE = new HashSet<>();
        List<int[]> pairs = new ArrayList<>();
        for (Candidate c : candidates) {
            if (!usedA.contains(c.a) && !usedE.contains(c.e)) {
                usedA.add(c.a);
                usedE.add(c.e);
                pairs.add(new int[]{c.a, c.e});
            }
        }
        return pairs;
    }

    static double similarity(String a, String b) {
        Set<String> x = words(a);
        Set<String> y = words(b);
        if (x.isEmpty() || y.isEmpty()) {
            return 0;
        }
        Set<String> intersection = new HashSet<>(x);
        intersection.retainAll(y);
        Set<String> union = new HashSet<>(x);
        union.addAll(y);
        return (double) intersection.size() / union.size();
    }

    private static Set<String> words(String text) {
        return Arrays.stream(text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+"))
                .filter(w -> w.length() > 1 && !STOP_WORDS.contains(w))
                .map(w -> w.endsWith("s") && w.length() > 3 ? w.substring(0, w.length() - 1) : w)
                .collect(Collectors.toSet());
    }

    private static boolean sameOwner(String actual, String expected) {
        if (actual == null || expected == null) {
            return actual == null && expected == null;
        }
        String first = actual.trim().toLowerCase(Locale.ROOT).split("\\s+")[0];
        return first.equals(expected.trim().toLowerCase(Locale.ROOT).split("\\s+")[0]);
    }
}
