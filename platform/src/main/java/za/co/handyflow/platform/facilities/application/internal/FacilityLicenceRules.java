package za.co.handyflow.platform.facilities.application.internal;

import java.util.Set;

/** When a licence expiry warrants a reminder: on the milestone days before, and once the day after it lapses. */
public final class FacilityLicenceRules {

    public static final Set<Long> REMINDER_DAYS = Set.of(30L, 14L, 7L, 3L, 1L, 0L);
    public static final long LAPSED_ALERT_DAY = -1L;

    private FacilityLicenceRules() {}

    public static boolean shouldRemind(long daysToExpiry) {
        return daysToExpiry == LAPSED_ALERT_DAY || REMINDER_DAYS.contains(daysToExpiry);
    }
}
