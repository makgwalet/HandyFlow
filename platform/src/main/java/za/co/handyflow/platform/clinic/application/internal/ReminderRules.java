package za.co.handyflow.platform.clinic.application.internal;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/** Pure rules for the automatic appointment-reminder sweep. */
final class ReminderRules {

    static final ZoneId CLINIC_ZONE = ZoneId.of("Africa/Johannesburg");

    /** Remind patients whose appointment starts within this long. */
    static final Duration LOOK_AHEAD = Duration.ofHours(24);
    /** Not worth reminding about an appointment that starts sooner than this (it was booked at the last minute). */
    static final Duration MIN_NOTICE = Duration.ofHours(2);
    /** Reminders go out between 08:00 and 19:59 clinic time, never in the middle of the night. */
    static final int FIRST_HOUR = 8;
    static final int LAST_HOUR = 19;

    private ReminderRules() {}

    static boolean inSendingHours(Instant now, ZoneId zone) {
        int h = now.atZone(zone).getHour();
        return h >= FIRST_HOUR && h <= LAST_HOUR;
    }

    /** Start of the window (inclusive): appointments sooner than this get no reminder. */
    static Instant from(Instant now) {
        return now.plus(MIN_NOTICE);
    }

    /** End of the window (exclusive). */
    static Instant to(Instant now) {
        return now.plus(LOOK_AHEAD);
    }
}
