package za.co.handyflow.platform.internalaudit.application.internal;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

/**
 * How the dashboard's headline figures have moved. The comparison is with the saved day nearest to a quarter ago that is at least a week old.
 * Where nothing was saved that long ago, the earlier figures are worked out from the dates already recorded (see {@link HistoricalMetrics}) and
 * the result says so. With records less than a week old there is no comparison, and the screen says so rather than showing an arrow.
 */
public final class DashboardTrends {
    private DashboardTrends() {}

    /** A saved day must be at least this old to compare with. */
    public static final int MIN_AGE_DAYS = 7;
    /** The age the comparison aims for: a quarter. */
    public static final int TARGET_AGE_DAYS = 90;

    public record Metrics(int engagements, int openFindings, int overdueActions, int highRiskAreas) {}
    public record Snapshot(LocalDate day, Metrics metrics) {}
    public record Change(String key, int now, int before, int delta) {}
    /** `since` and `daysAgo` are null, and `changes` empty, when there is nothing old enough to compare with. */
    public record Trends(LocalDate since, Integer daysAgo, List<Change> changes, String basis) {}
    public static final String SAVED = "SAVED", RECONSTRUCTED = "RECONSTRUCTED";

    public static Trends compare(Metrics now, List<Snapshot> history, LocalDate today) {
        Snapshot base = history.stream()
                .filter(s -> s.day() != null && ChronoUnit.DAYS.between(s.day(), today) >= MIN_AGE_DAYS)
                .min(Comparator.<Snapshot>comparingLong(s -> Math.abs(ChronoUnit.DAYS.between(s.day(), today) - TARGET_AGE_DAYS))
                        .thenComparing(Snapshot::day))
                .orElse(null);
        if (base == null) return none();
        return against(now, base.day(), base.metrics(), today, SAVED);
    }

    public static Trends none() { return new Trends(null, null, List.of(), null); }

    /** The comparison with figures for `day`, whichever way they were found. */
    public static Trends against(Metrics now, LocalDate day, Metrics before, LocalDate today, String basis) {
        return new Trends(day, (int) ChronoUnit.DAYS.between(day, today), List.of(
                change("engagements", now.engagements(), before.engagements()),
                change("openFindings", now.openFindings(), before.openFindings()),
                change("overdueActions", now.overdueActions(), before.overdueActions()),
                change("highRiskAreas", now.highRiskAreas(), before.highRiskAreas())), basis);
    }

    private static Change change(String key, int now, int before) { return new Change(key, now, before, now - before); }
}
