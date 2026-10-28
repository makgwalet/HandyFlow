package za.co.handyflow.platform.recruiter.domain.model;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** The dashboard's period and comparison rules. */
public final class RecruiterDashboardRules {
    private RecruiterDashboardRules() {}

    public static final List<Integer> PERIODS = List.of(7, 30, 90);
    public static final int DEFAULT_DAYS = 30;

    /** Null when the number of days is one the dashboard offers. */
    public static String daysProblem(Integer days) {
        return days == null || PERIODS.contains(days) ? null : "Choose 7, 30 or 90 days.";
    }

    public static int daysOrDefault(Integer days) { return days == null ? DEFAULT_DAYS : days; }

    /** Start of the period that ends at {@code now}. */
    public static Instant periodStart(Instant now, int days) { return now.minus(days, ChronoUnit.DAYS); }

    /** Start of the period of the same length just before the current one. */
    public static Instant previousStart(Instant now, int days) { return now.minus(2L * days, ChronoUnit.DAYS); }

    /** Percentage change from previous to current, one decimal. Null when there is nothing to compare with (previous or current missing, or previous zero). */
    public static Double changePercent(Double current, Double previous) {
        if (current == null || previous == null || previous == 0) return null;
        return Math.round((current - previous) / previous * 1000.0) / 10.0;
    }
}
