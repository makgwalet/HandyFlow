package za.co.handyflow.platform.invoicing.application.internal;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** Rules for recurring invoices delayed by the hard invoice cap. Pure. */
public final class RecurringQueueRules {

    private RecurringQueueRules() {}

    /**
     * A due occurrence waits when the cap does not allow it, and also when earlier occurrences are already waiting, so
     * the oldest is always issued first.
     */
    public static boolean shouldQueue(boolean capEnforced, boolean capAllows, boolean earlierOccurrencesWaiting) {
        return capEnforced && (!capAllows || earlierOccurrencesWaiting);
    }

    /** The last day the occurrence covers: the day before the next one starts (never before it starts). */
    public static LocalDate periodEnd(LocalDate periodStart, LocalDate nextOccurrence) {
        LocalDate end = nextOccurrence.minusDays(1);
        return end.isBefore(periodStart) ? periodStart : end;
    }

    public static long daysDelayed(LocalDate scheduledFor, LocalDate today) {
        return Math.max(0, ChronoUnit.DAYS.between(scheduledFor, today));
    }

    /** Overdue once it has waited longer than the configured number of days, counted from the original billing date. */
    public static boolean overdue(LocalDate scheduledFor, LocalDate today, int maxDelayDays) {
        return daysDelayed(scheduledFor, today) > Math.max(0, maxDelayDays);
    }

    /** The title shown on the invoice, so the customer can see which period it covers even though it was issued late. */
    public static String delayedTitle(String title, LocalDate periodStart, LocalDate periodEnd) {
        return title + " (service period " + periodStart + " to " + periodEnd + ")";
    }
}
