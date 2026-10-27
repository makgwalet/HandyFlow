package za.co.handyflow.platform.facilities.application.internal;

import java.time.LocalDate;
import java.util.Set;

/** When a recurring inspection is next due, and when to remind about it. Pure, so it is easy to test. */
public final class FacilityInspectionScheduleRules {

    /** Remind this many days before the due date (and on the day). */
    private static final Set<Long> LEAD_DAYS = Set.of(7L, 3L, 1L, 0L);

    private FacilityInspectionScheduleRules() {}

    /**
     * The cadence stays anchored to the old due date, so finishing a day early or late does not shift the calendar
     * (a monthly check stays monthly). If the next date from that anchor would already be past, the schedule had been
     * neglected; restart from the day it was done instead of leaving it overdue the moment it is completed.
     */
    public static LocalDate nextDue(LocalDate currentDue, int frequencyDays, LocalDate completedOn) {
        LocalDate anchored = currentDue.plusDays(frequencyDays);
        return anchored.isAfter(completedOn) ? anchored : completedOn.plusDays(frequencyDays);
    }

    /**
     * @param daysUntilDue positive before the due date, 0 on the day, negative once overdue
     * @return true on the lead days and, once overdue, on the overdue milestones (shared with work orders)
     */
    public static boolean shouldRemind(long daysUntilDue) {
        if (daysUntilDue >= 0) return LEAD_DAYS.contains(daysUntilDue);
        return FacilityOverduePolicy.shouldAlert(-daysUntilDue);
    }
}
