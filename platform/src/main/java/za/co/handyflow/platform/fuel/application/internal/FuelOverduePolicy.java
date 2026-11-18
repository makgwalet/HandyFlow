package za.co.handyflow.platform.fuel.application.internal;

import java.time.Duration;
import java.time.Instant;

/**
 * The one definition of "overdue" for a delivery, used by the dashboard, the push alerts and the daily notifications, so they cannot disagree.
 * Measured from the delivery's scheduled time (an authorised reschedule moves that time, so the clock restarts with it). An instant is
 * timezone-free, so the company's timezone only matters when a time is shown.
 * <ul>
 *   <li>before the scheduled time: ON_SCHEDULE</li>
 *   <li>within the grace period (default 120 minutes) after it: DELAYED, shown but not alerted</li>
 *   <li>beyond the grace: OVERDUE (dashboard count and push)</li>
 *   <li>beyond the grace plus the company's escalation period: ESCALATED (the next responsible role is told)</li>
 * </ul>
 */
public final class FuelOverduePolicy {
    private FuelOverduePolicy() {}

    public static final int DEFAULT_GRACE_MINUTES = 120;
    public static final int DEFAULT_ESCALATION_MINUTES = 60;
    /** The smallest grace a company can set; used to narrow queries before each company's own grace is applied. */
    public static final Duration SHORTEST_GRACE = Duration.ofMinutes(15);

    public enum Status { ON_SCHEDULE, DELAYED, OVERDUE, ESCALATED }

    public static Instant overdueAt(Instant scheduledAt, int graceMinutes) { return scheduledAt.plus(Duration.ofMinutes(graceMinutes)); }

    public static Status status(Instant scheduledAt, Instant now, int graceMinutes, int escalationMinutes) {
        if (scheduledAt == null || !now.isAfter(scheduledAt)) return Status.ON_SCHEDULE;
        Instant overdueAt = overdueAt(scheduledAt, graceMinutes);
        if (now.isBefore(overdueAt)) return Status.DELAYED;
        return now.isBefore(overdueAt.plus(Duration.ofMinutes(escalationMinutes))) ? Status.OVERDUE : Status.ESCALATED;
    }

    /** Overdue or escalated: the same test for every screen and alert. */
    public static boolean isOverdue(Instant scheduledAt, Instant now, int graceMinutes) {
        return scheduledAt != null && !now.isBefore(overdueAt(scheduledAt, graceMinutes));
    }
}
