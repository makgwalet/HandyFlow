package za.co.handyflow.platform.hr.domain.model;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * How often something that is late is worth a message: the day after it falls due and then, if it matters enough to keep
 * asking about, every seventh day. Pure: no database and no clock.
 */
public final class ReminderCadence {

    public static final int REPEAT_DAYS = 7;

    private ReminderCadence() {}

    public static long daysLate(LocalDate due, LocalDate today) {
        return due == null ? 0 : ChronoUnit.DAYS.between(due, today);
    }

    public static boolean dueToday(LocalDate due, LocalDate today, boolean repeat) {
        long late = daysLate(due, today);
        if (late < 1) return false;
        return late == 1 || (repeat && late % REPEAT_DAYS == 0);
    }
}
