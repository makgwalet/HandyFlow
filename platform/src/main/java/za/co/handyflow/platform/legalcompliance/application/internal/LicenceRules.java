package za.co.handyflow.platform.legalcompliance.application.internal;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Pure date rules for licences, permits and certificates. "Expired" is always derived from the expiry date and today; it is never a stored status.
 * A licence with no expiry date never expires and never triggers a reminder.
 */
public final class LicenceRules {

    private LicenceRules() {}

    public static final int CONDITION_WARNING_DAYS = 14;
    private static final int[] FIXED_REMINDER_DAYS = {30, 14, 7, 3, 1, 0};

    public static boolean expired(LocalDate expiry, LocalDate today) { return expiry != null && today.isAfter(expiry); }

    /** Days until expiry; negative once expired; null when there is no expiry date. */
    public static Long daysToExpiry(LocalDate expiry, LocalDate today) { return expiry == null ? null : ChronoUnit.DAYS.between(today, expiry); }

    public static boolean expiringSoon(LocalDate expiry, int leadDays, LocalDate today) {
        Long d = daysToExpiry(expiry, today);
        return d != null && d >= 0 && d <= leadDays;
    }

    /**
     * True on the day the renewal lead time starts, then at 30, 14, 7, 3, 1 and 0 days (each only when inside the lead time), and weekly once expired.
     * Called by a daily sweep, so each day that qualifies sends one reminder and the others none.
     */
    public static boolean reminderDue(LocalDate expiry, int leadDays, LocalDate today) {
        Long d = daysToExpiry(expiry, today);
        if (d == null) return false;
        if (d < 0) return (-d) % 7 == 0;
        if (d == leadDays) return true;
        for (int fixed : FIXED_REMINDER_DAYS) if (d == fixed && fixed <= leadDays) return true;
        return false;
    }

    public static boolean conditionOverdue(LocalDate due, LocalDate today) { return due != null && today.isAfter(due); }

    public static boolean conditionDueSoon(LocalDate due, LocalDate today) {
        return due != null && !today.isAfter(due) && ChronoUnit.DAYS.between(today, due) <= CONDITION_WARNING_DAYS;
    }

    /** Reminders for a pending condition: 14, 7, 1 and 0 days before it is due, then weekly while overdue. */
    public static boolean conditionReminderDue(LocalDate due, LocalDate today) {
        if (due == null) return false;
        long d = ChronoUnit.DAYS.between(today, due);
        if (d < 0) return (-d) % 7 == 0;
        return d == 14 || d == 7 || d == 1 || d == 0;
    }

    /** Next due date of a recurring condition after it is met on {@code metOn}: the first date in the series that falls after it. */
    public static LocalDate nextConditionDue(LocalDate currentDue, int frequencyMonths, LocalDate metOn) {
        LocalDate next = currentDue.plusMonths(frequencyMonths);
        int guard = 0;
        while (!next.isAfter(metOn) && guard++ < 1200) next = next.plusMonths(frequencyMonths);
        return next;
    }
}
