package za.co.handyflow.platform.facilities.application.internal;

import java.util.Set;

/**
 * When to remind about a work order that is past its scheduled date. The daily sweep used to alert every day for as
 * long as the job stayed open, which trains people to ignore it. Now: day 1, 3, 7, 14 and 30 past due, then every
 * 30 days.
 */
public final class FacilityOverduePolicy {

    private static final Set<Long> MILESTONES = Set.of(1L, 3L, 7L, 14L, 30L);

    private FacilityOverduePolicy() {}

    public static boolean shouldAlert(long daysOverdue) {
        if (daysOverdue < 1) return false;
        if (MILESTONES.contains(daysOverdue)) return true;
        return daysOverdue > 30 && daysOverdue % 30 == 0;
    }
}
