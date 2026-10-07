// security/application/internal/ShiftPunctuality.java
package za.co.handyflow.platform.security.application.internal;

import java.time.Duration;
import java.time.Instant;

/**
 * Whether a guard was late, judged from the moment the shift was really started. Pure, so it can be tested directly.
 * A start up to GRACE_MINUTES after the scheduled start is on time (the same grace the late-arrival alert uses).
 * A shift with no recorded start (it started before the start time was kept) is judged on the alert, as it used to be.
 */
public final class ShiftPunctuality {

    private ShiftPunctuality() {}

    public static final int GRACE_MINUTES = 15;

    /** Whole minutes after the scheduled start, never negative; null when the shift has no recorded start. */
    public static Integer minutesLate(Instant scheduledStart, Instant actualStart) {
        if (scheduledStart == null || actualStart == null) return null;
        return (int) Math.max(0, Duration.between(scheduledStart, actualStart).toMinutes());
    }

    public static boolean late(Instant scheduledStart, Instant actualStart, Instant lateAlertSentAt) {
        Integer minutes = minutesLate(scheduledStart, actualStart);
        if (minutes != null) return minutes > GRACE_MINUTES;
        return lateAlertSentAt != null;
    }
}
