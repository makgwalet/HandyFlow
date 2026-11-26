package za.co.handyflow.platform.trainingprovider.domain.rules;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * The provider's own moderation sampling. Given a saved rule version and the decided attempts in a window, this works out how many to
 * moderate and which. It holds no percentages or thresholds of its own and says nothing about what any regulator requires: the numbers
 * come from the rule the provider wrote. The same seed and the same population always give the same selection, so a draw can be checked.
 */
public final class SamplingRules {

    public static final String ALL = "ALL";
    public static final String PERCENTAGE = "PERCENTAGE";
    public static final String RANDOM = "RANDOM";
    public static final String RISK_BASED = "RISK_BASED";
    public static final String R_ALL = "ALL";
    public static final String R_RANDOM = "RANDOM";
    public static final String R_RISK = "RISK";
    public static final String R_TARGETED = "TARGETED";
    public static final String R_EXTRA = "EXTRA_AFTER_POOR";
    public static final int MIN_REASON = 10;

    private SamplingRules() {
    }

    /** The settings of one rule version. */
    public record Settings(String basis, BigDecimal percentage, Integer minCount, Integer maxCount, String selectionMethod,
                           Integer extraTriggerQueries, Integer extraLookback, BigDecimal extraPercentage) {
    }

    /** One decided attempt in the population. */
    public record Candidate(UUID attemptId, UUID assessorId, int riskScore) {
    }

    /** Which attempts were picked, and why. */
    public record Pick(UUID attemptId, String reason, String targetNote) {
    }

    public record Outcome(int eligible, int required, int extraRequired, List<Pick> picks) {
    }

    /** Problems with rule settings, or null when they hold together. */
    public static String settingsProblem(Settings s) {
        if (s == null) {
            return "Settings are required.";
        }
        if (!ALL.equals(s.basis()) && !PERCENTAGE.equals(s.basis())) {
            return "Choose whether every record is moderated or a percentage.";
        }
        if (PERCENTAGE.equals(s.basis())) {
            if (s.percentage() == null || s.percentage().signum() <= 0 || s.percentage().compareTo(BigDecimal.valueOf(100)) > 0) {
                return "The percentage must be above 0 and no more than 100.";
            }
        } else if (s.percentage() != null) {
            return "A rule that moderates every record has no percentage.";
        }
        if (s.minCount() != null && s.minCount() < 0) {
            return "The minimum cannot be negative.";
        }
        if (s.maxCount() != null && s.maxCount() < 1) {
            return "The maximum must be at least 1.";
        }
        if (s.minCount() != null && s.maxCount() != null && s.maxCount() < s.minCount()) {
            return "The maximum cannot be below the minimum.";
        }
        if (!RANDOM.equals(s.selectionMethod()) && !RISK_BASED.equals(s.selectionMethod())) {
            return "Choose random or risk-based selection.";
        }
        boolean any = s.extraTriggerQueries() != null || s.extraLookback() != null || s.extraPercentage() != null;
        if (any) {
            if (s.extraTriggerQueries() == null || s.extraLookback() == null || s.extraPercentage() == null) {
                return "Extra sampling needs the trigger, the look-back and the extra percentage together.";
            }
            if (s.extraTriggerQueries() < 1 || s.extraLookback() < s.extraTriggerQueries()) {
                return "The look-back must be at least the trigger, and the trigger at least 1.";
            }
            if (s.extraPercentage().signum() <= 0 || s.extraPercentage().compareTo(BigDecimal.valueOf(100)) > 0) {
                return "The extra percentage must be above 0 and no more than 100.";
            }
        }
        return null;
    }

    /** How many records the rule asks for from a population, before any extra sampling. Never more than the population. */
    public static int requiredCount(Settings s, int eligible) {
        if (eligible <= 0) {
            return 0;
        }
        int n;
        if (ALL.equals(s.basis())) {
            n = eligible;
        } else {
            n = s.percentage().multiply(BigDecimal.valueOf(eligible)).divide(BigDecimal.valueOf(100), 0, RoundingMode.CEILING).intValue();
        }
        if (s.minCount() != null) {
            n = Math.max(n, s.minCount());
        }
        if (s.maxCount() != null && !ALL.equals(s.basis())) {
            n = Math.min(n, s.maxCount());
        }
        return Math.min(n, eligible);
    }

    /**
     * Whether recent moderation results ask for extra sampling. The recent results are newest first; each is true when the moderation
     * raised a query (a finding that did not agree with the assessor).
     */
    public static boolean extraTriggered(Settings s, List<Boolean> recentQueryRaised) {
        if (s.extraTriggerQueries() == null || recentQueryRaised == null) {
            return false;
        }
        int look = Math.min(s.extraLookback(), recentQueryRaised.size());
        int queries = 0;
        for (int i = 0; i < look; i++) {
            if (Boolean.TRUE.equals(recentQueryRaised.get(i))) {
                queries++;
            }
        }
        return queries >= s.extraTriggerQueries();
    }

    /** The count asked for once extra sampling is added. */
    public static int extraCount(Settings s, int eligible, int required) {
        if (eligible <= 0 || s.extraPercentage() == null) {
            return 0;
        }
        int extra = s.extraPercentage().multiply(BigDecimal.valueOf(eligible)).divide(BigDecimal.valueOf(100), 0, RoundingMode.CEILING).intValue();
        return Math.max(0, Math.min(extra, eligible - required));
    }

    /**
     * Picks the records. Targeted ones are always in, then the rest are filled by the rule's method up to the required count, then extra
     * sampling when triggered. Random uses the seed; risk-based takes the highest scores first and breaks ties by the seeded order.
     */
    public static Outcome select(Settings s, List<Candidate> population, Map<UUID, String> targeted, boolean extraTriggered, long seed) {
        int eligible = population.size();
        int required = requiredCount(s, eligible);
        int extra = extraTriggered ? extraCount(s, eligible, required) : 0;
        Map<UUID, Pick> picks = new LinkedHashMap<>();
        if (targeted != null) {
            for (Candidate c : population) {
                String note = targeted.get(c.attemptId());
                if (note != null) {
                    picks.put(c.attemptId(), new Pick(c.attemptId(), R_TARGETED, note));
                }
            }
        }
        List<Candidate> order = new ArrayList<>(population);
        Random rnd = new Random(seed);
        Map<UUID, Double> tiebreak = new LinkedHashMap<>();
        // Seeded order depends on the attempt ids sorted first, so the result does not depend on how the population was listed.
        order.sort(Comparator.comparing(c -> c.attemptId().toString()));
        for (Candidate c : order) {
            tiebreak.put(c.attemptId(), rnd.nextDouble());
        }
        if (RISK_BASED.equals(s.selectionMethod())) {
            order.sort(Comparator.<Candidate>comparingInt(c -> -c.riskScore()).thenComparing(c -> tiebreak.get(c.attemptId())));
        } else {
            order.sort(Comparator.comparing(c -> tiebreak.get(c.attemptId())));
        }
        String reason = ALL.equals(s.basis()) ? R_ALL : (RISK_BASED.equals(s.selectionMethod()) ? R_RISK : R_RANDOM);
        for (Candidate c : order) {
            if (picks.size() >= required) {
                break;
            }
            picks.putIfAbsent(c.attemptId(), new Pick(c.attemptId(), reason, null));
        }
        int target = Math.min(eligible, required + extra);
        for (Candidate c : order) {
            if (picks.size() >= target) {
                break;
            }
            picks.putIfAbsent(c.attemptId(), new Pick(c.attemptId(), R_EXTRA, null));
        }
        return new Outcome(eligible, required, extra, new ArrayList<>(picks.values()));
    }

    /** Problem with a note given for a targeted record or a corrective action, or null. */
    public static String noteProblem(String note) {
        if (note == null || note.trim().length() < MIN_REASON) {
            return "Give a reason of at least " + MIN_REASON + " characters.";
        }
        return null;
    }

    /** A draw can only be closed once every picked record has a moderation. */
    public static String closeProblem(int picked, int moderated, String decisionNote) {
        if (moderated < picked) {
            return (picked - moderated) + " selected record(s) have no moderation yet.";
        }
        return noteProblem(decisionNote);
    }
}
