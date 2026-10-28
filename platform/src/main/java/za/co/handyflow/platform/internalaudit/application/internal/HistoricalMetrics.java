package za.co.handyflow.platform.internalaudit.application.internal;

import za.co.handyflow.platform.internalaudit.application.internal.DashboardTrends.Metrics;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;

/**
 * The dashboard's headline figures as they stood at the end of an earlier day, worked out from the dates already recorded
 * (when things were created, resolved, closed, validated or assessed). Used when no figures were saved that long ago.
 * Pure; no database.
 * <p>
 * It is an estimate, and the screen says so. It cannot see changes that were not dated: a finding that was closed and later reopened counts as
 * open throughout; a cancelled action is left out for good; an area switched off since is left out; and an assessment whose level was
 * overridden later shows its overridden level.
 */
public final class HistoricalMetrics {
    private HistoricalMetrics() {}

    private static final ZoneId ZONE = ZoneId.of("Africa/Johannesburg");

    public record EngagementIn(Instant createdAt) {}
    public record FindingIn(Instant createdAt, Instant resolvedAt, Instant closedAt, String status) {}
    public record ActionIn(Instant createdAt, LocalDate dueDate, Instant validatedAt, String status) {}
    public record RatingIn(Instant assessedAt, String finalRisk) {}
    /** An area still in the universe, with every rating it has had. */
    public record AreaIn(Instant createdAt, List<RatingIn> ratings) {}

    /** The figures at the end of `day`. */
    public static Metrics asAt(LocalDate day, List<EngagementIn> engagements, List<FindingIn> findings, List<ActionIn> actions, List<AreaIn> areas) {
        Instant end = day.plusDays(1).atStartOfDay(ZONE).toInstant();
        int eng = (int) engagements.stream().filter(e -> before(e.createdAt(), end)).count();
        int open = (int) findings.stream().filter(f -> before(f.createdAt(), end) && openAt(f, end)).count();
        int overdue = (int) actions.stream().filter(a -> before(a.createdAt(), end) && overdueAt(a, day, end)).count();
        int high = (int) areas.stream().filter(a -> before(a.createdAt(), end) && highAt(a, end)).count();
        return new Metrics(eng, open, overdue, high);
    }

    /** The day the oldest record was made, or null when there are none. */
    public static LocalDate earliest(List<EngagementIn> engagements, List<FindingIn> findings, List<ActionIn> actions, List<AreaIn> areas) {
        return java.util.stream.Stream.of(
                        engagements.stream().map(EngagementIn::createdAt), findings.stream().map(FindingIn::createdAt),
                        actions.stream().map(ActionIn::createdAt), areas.stream().map(AreaIn::createdAt))
                .flatMap(s -> s).filter(java.util.Objects::nonNull).min(Comparator.naturalOrder())
                .map(i -> i.atZone(ZONE).toLocalDate()).orElse(null);
    }

    private static boolean before(Instant at, Instant end) { return at != null && at.isBefore(end); }

    private static boolean openAt(FindingIn f, Instant end) {
        if (InternalAuditDashboardBuilder.isOpen(f.status())) return true;           // open now: counted as open since it was raised
        Instant ended = java.util.stream.Stream.of(f.resolvedAt(), f.closedAt()).filter(java.util.Objects::nonNull).min(Comparator.naturalOrder()).orElse(null);
        return ended != null && !ended.isBefore(end);                          // ended on a later day: still open then
    }

    private static boolean overdueAt(ActionIn a, LocalDate day, Instant end) {
        if ("CANCELLED".equals(a.status()) || a.dueDate() == null || !a.dueDate().isBefore(day)) return false;
        return a.validatedAt() == null || !a.validatedAt().isBefore(end);      // an action stays overdue until it is validated
    }

    private static boolean highAt(AreaIn a, Instant end) {
        String level = a.ratings().stream().filter(r -> before(r.assessedAt(), end)).max(Comparator.comparing(RatingIn::assessedAt)).map(RatingIn::finalRisk).orElse(null);
        return "HIGH".equals(level) || "CRITICAL".equals(level);
    }
}
