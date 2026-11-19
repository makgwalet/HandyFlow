package za.co.handyflow.platform.legalcompliance.application.internal;

import za.co.handyflow.platform.legalcompliance.domain.model.EsgDirection;
import za.co.handyflow.platform.legalcompliance.domain.model.EsgFrequency;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

/** Pure rules for ESG metrics. Whether a reading met its target is worked out from the target that applied when it was recorded. */
public final class EsgRules {

    private EsgRules() {}

    public static final int GRACE_DAYS = 30;

    public enum TargetStatus { MET, MISSED, NONE }

    public static int stepMonths(EsgFrequency f) {
        return switch (f) { case MONTHLY -> 1; case QUARTERLY -> 3; case ANNUALLY -> 12; };
    }

    /** End of the next period after one ending on {@code latestEnd}; a period that ended on a month-end keeps ending on a month-end. */
    public static LocalDate nextPeriodEnd(EsgFrequency f, LocalDate latestEnd) {
        int step = stepMonths(f);
        boolean monthEnd = latestEnd.equals(YearMonth.from(latestEnd).atEndOfMonth());
        return monthEnd ? YearMonth.from(latestEnd).plusMonths(step).atEndOfMonth() : latestEnd.plusMonths(step);
    }

    /** A reading is overdue once the period after the latest one has ended and the grace has passed. With no reading yet, one period from the day the metric was recorded. */
    public static boolean readingOverdue(EsgFrequency f, LocalDate latestPeriodEnd, LocalDate recordedOn, LocalDate today) {
        LocalDate expectedEnd = latestPeriodEnd != null ? nextPeriodEnd(f, latestPeriodEnd) : recordedOn.plusMonths(stepMonths(f));
        return today.isAfter(expectedEnd.plusDays(GRACE_DAYS));
    }

    public static TargetStatus targetStatus(EsgDirection d, BigDecimal target, BigDecimal value) {
        if (d == null || d == EsgDirection.TRACK_ONLY || target == null || value == null) return TargetStatus.NONE;
        int c = value.compareTo(target);
        return (d == EsgDirection.LOWER_IS_BETTER ? c <= 0 : c >= 0) ? TargetStatus.MET : TargetStatus.MISSED;
    }
}
