package za.co.handyflow.platform.accounting.application.internal;

import java.time.LocalDate;

/** The books' lock date (finance patch F5, 2026-10-14). Pure, so it is tested without Spring. */
public final class PeriodLockRules {

    public static final int MIN_REOPEN_REASON = 10;

    private PeriodLockRules() {}

    /** Null when a journal dated {@code entryDate} may be created or posted, otherwise why not. */
    public static String postProblem(LocalDate entryDate, LocalDate lockedThrough) {
        if (lockedThrough == null || entryDate == null || entryDate.isAfter(lockedThrough)) return null;
        return "The books are closed up to and including " + lockedThrough + ". Date this journal "
                + lockedThrough.plusDays(1) + " or later, or ask someone allowed to reopen the period";
    }

    /** Null when the books may be closed up to {@code newDate}, otherwise why not. */
    public static String lockProblem(LocalDate newDate, LocalDate current, LocalDate today, long draftsInPeriod) {
        if (newDate == null) return "Choose the last day to close";
        if (newDate.isAfter(today)) return "The books cannot be closed past today";
        if (current != null && !newDate.isAfter(current))
            return "The books are already closed up to " + current + ". To move the date back, reopen the period";
        if (draftsInPeriod > 0)
            return draftsInPeriod + (draftsInPeriod == 1 ? " draft journal is" : " draft journals are")
                    + " dated on or before " + newDate + ". Post or discard them first";
        return null;
    }

    /** Null when the lock may move back to {@code newDate} (null opens everything), otherwise why not. */
    public static String reopenProblem(LocalDate newDate, LocalDate current, String reason) {
        if (current == null) return "No period is closed";
        if (newDate != null && !newDate.isBefore(current))
            return "To reopen, choose a date before the current lock date " + current;
        if (reason == null || reason.trim().length() < MIN_REOPEN_REASON)
            return "Say why the period is being reopened (at least " + MIN_REOPEN_REASON + " characters)";
        if (reason.trim().length() > 500) return "Keep the reason to 500 characters";
        return null;
    }
}
