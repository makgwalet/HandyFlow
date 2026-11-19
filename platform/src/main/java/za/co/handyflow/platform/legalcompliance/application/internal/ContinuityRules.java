package za.co.handyflow.platform.legalcompliance.application.internal;

import za.co.handyflow.platform.legalcompliance.domain.model.Criticality;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Pure rules for business continuity. Overdue states are derived from dates and today; none is stored. */
public final class ContinuityRules {

    private ContinuityRules() {}

    /** How often a process's plan must be exercised, by how critical the process is. */
    public static int testIntervalMonths(Criticality c) {
        return switch (c) { case CRITICAL -> 6; case HIGH, MEDIUM -> 12; case LOW -> 24; };
    }

    /** Anchored on the last exercise, or on approval of the plan if it has never been exercised. No plan, nothing to exercise. */
    public static boolean testOverdue(Criticality c, LocalDate lastTest, LocalDate approvedOn, LocalDate today) {
        LocalDate anchor = lastTest != null ? lastTest : approvedOn;
        return anchor != null && today.isAfter(anchor.plusMonths(testIntervalMonths(c)));
    }

    public static LocalDate testDueOn(Criticality c, LocalDate lastTest, LocalDate approvedOn) {
        LocalDate anchor = lastTest != null ? lastTest : approvedOn;
        return anchor == null ? null : anchor.plusMonths(testIntervalMonths(c));
    }

    /** Anchored on the last BIA review, or on the day the process was recorded. */
    public static boolean biaOverdue(LocalDate reviewedOn, int months, LocalDate recordedOn, LocalDate today) {
        LocalDate anchor = reviewedOn != null ? reviewedOn : recordedOn;
        return today.isAfter(anchor.plusMonths(months));
    }

    public static boolean planReviewOverdue(LocalDate nextReviewDue, LocalDate today) { return nextReviewDue != null && today.isAfter(nextReviewDue); }

    /** null when no recovery time was measured. */
    public static Boolean rtoMet(BigDecimal achievedHours, int targetHours) {
        return achievedHours == null ? null : achievedHours.compareTo(BigDecimal.valueOf(targetHours)) <= 0;
    }
}
