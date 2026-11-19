package za.co.handyflow.platform.legalcompliance.application.internal;

import za.co.handyflow.platform.legalcompliance.domain.model.RiskPosition;
import za.co.handyflow.platform.legalcompliance.domain.model.RiskRating;

/**
 * Pure 5x5 risk scoring. score = likelihood x impact (1..25).
 * Rating bands are fixed: 1-4 LOW, 5-9 MEDIUM, 10-16 HIGH, 17-25 CRITICAL.
 * Appetite and tolerance are per-tenant score thresholds: a score at or below appetite is acceptable as it stands; above appetite
 * it needs treatment; above tolerance it is unacceptable and may not be formally accepted.
 */
public final class RiskScoringRules {
    private RiskScoringRules() {}

    public static final int MIN = 1, MAX = 5;

    public static int score(int likelihood, int impact) {
        requireScale(likelihood, "Likelihood");
        requireScale(impact, "Impact");
        return likelihood * impact;
    }

    public static RiskRating rating(int score) {
        if (score < 1 || score > 25) throw new IllegalArgumentException("Score must be between 1 and 25");
        if (score <= 4) return RiskRating.LOW;
        if (score <= 9) return RiskRating.MEDIUM;
        if (score <= 16) return RiskRating.HIGH;
        return RiskRating.CRITICAL;
    }

    public static RiskPosition position(int score, int appetiteMax, int toleranceMax) {
        if (score <= appetiteMax) return RiskPosition.WITHIN_APPETITE;
        if (score <= toleranceMax) return RiskPosition.ABOVE_APPETITE;
        return RiskPosition.OUTSIDE_TOLERANCE;
    }

    public static void validateThresholds(int appetiteMax, int toleranceMax) {
        if (appetiteMax < 1 || toleranceMax > 25 || appetiteMax >= toleranceMax)
            throw new IllegalArgumentException("Appetite must be at least 1 and lower than tolerance, and tolerance at most 25");
    }

    private static void requireScale(int v, String what) {
        if (v < MIN || v > MAX) throw new IllegalArgumentException(what + " must be between 1 and 5");
    }
}
