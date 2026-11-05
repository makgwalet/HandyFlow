package za.co.handyflow.platform.hr.domain.model;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

/** Rules for suspending and reinstating an employee. Termination has its own rules (EmployeeRules). Pure. */
public final class EmployeeStatusRules {

    public static final String ACTIVE = "ACTIVE", SUSPENDED = "SUSPENDED";
    public static final int MIN_REASON = 10;
    static final ZoneId SA = ZoneId.of("Africa/Johannesburg");

    private EmployeeStatusRules() {}

    /** Null when the change is allowed. Only ACTIVE to SUSPENDED and SUSPENDED to ACTIVE are status changes here. */
    public static String problem(String currentStatus, String toStatus, String reason, boolean emergency) {
        if (!ACTIVE.equals(toStatus) && !SUSPENDED.equals(toStatus)) return "Only suspension and reinstatement are handled here. Use the termination steps to end employment.";
        if (toStatus.equals(currentStatus)) return "This employee is already " + toStatus.toLowerCase() + ".";
        if (SUSPENDED.equals(toStatus) && !ACTIVE.equals(currentStatus)) return "Only an active employee can be suspended.";
        if (ACTIVE.equals(toStatus) && !SUSPENDED.equals(currentStatus)) return "Only a suspended employee can be reinstated.";
        if (emergency && !SUSPENDED.equals(toStatus)) return "Only a suspension can be an emergency.";
        if (reason == null || reason.trim().length() < MIN_REASON) return "Give the reason (at least " + MIN_REASON + " characters).";
        if (reason.trim().length() > 1000) return "The reason can be at most 1000 characters.";
        return null;
    }

    /**
     * An emergency suspension must be reviewed by the end of the next business day (Monday to Friday, South African time).
     * Public holidays are not modelled, so on a holiday the real deadline is later.
     */
    public static Instant reviewDueAt(Instant suspendedAt) {
        LocalDate day = suspendedAt.atZone(SA).toLocalDate().plusDays(1);
        while (day.getDayOfWeek() == DayOfWeek.SATURDAY || day.getDayOfWeek() == DayOfWeek.SUNDAY) day = day.plusDays(1);
        return LocalDateTime.of(day, LocalTime.of(23, 59)).atZone(SA).toInstant();
    }

    public static boolean reviewOverdue(Instant dueAt, Instant now) { return dueAt != null && now.isAfter(dueAt); }
}
