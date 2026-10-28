package za.co.handyflow.platform.internalaudit.application.internal;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

/**
 * How the dashboard's headline figures have moved. The comparison is with the saved day nearest to a quarter ago that is at least a week old;
 * with nothing that old there is no comparison, and the screen says so rather than showing an arrow.
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
    public record Trends(LocalDate since, Integer daysAgo, List<Change> changes) {}

    public static Trends compare(Metrics now, List<Snapshot> history, LocalDate today) {
        Snapshot base = history.stream()
                .filter(s -> s.day() != null && ChronoUnit.DAYS.between(s.day(), today) >= MIN_AGE_DAYS)
                .min(Comparator.<Snapshot>comparingLong(s -> Math.abs(ChronoUnit.DAYS.between(s.day(), today) - TARGET_AGE_DAYS))
                        .thenComparing(Snapshot::day))
                .orElse(null);
        if (base == null) return new Trends(null, null, List.of());
        Metrics b = base.metrics();
        return new Trends(base.day(), (int) ChronoUnit.DAYS.between(base.day(), today), List.of(
                change("engagements", now.engagements(), b.engagements()),
                change("openFindings", now.openFindings(), b.openFindings()),
                change("overdueActions", now.overdueActions(), b.overdueActions()),
                change("highRiskAreas", now.highRiskAreas(), b.highRiskAreas())));
    }

    private static Change change(String key, int now, int before) { return new Change(key, now, before, now - before); }
}
