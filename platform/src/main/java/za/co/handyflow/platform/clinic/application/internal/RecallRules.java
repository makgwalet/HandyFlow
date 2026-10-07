package za.co.handyflow.platform.clinic.application.internal;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/** Pure rules for follow-up recalls: dates are the clinic's own (South African) calendar days. */
final class RecallRules {

    static final ZoneId CLINIC_ZONE = ZoneId.of("Africa/Johannesburg");

    private RecallRules() {}

    /** The day the follow-up falls due: the visit's clinic-local date plus the follow-up days. */
    static LocalDate dueDate(Instant consultedAt, int followUpDays, ZoneId zone) {
        return consultedAt.atZone(zone).toLocalDate().plusDays(followUpDays);
    }

    /** Due today or earlier. */
    static boolean isDue(LocalDate dueDate, LocalDate today) {
        return !dueDate.isAfter(today);
    }

    /** Whole days past the due date; zero when not yet overdue. */
    static int overdueDays(LocalDate dueDate, LocalDate today) {
        return (int) Math.max(0, ChronoUnit.DAYS.between(dueDate, today));
    }
}
