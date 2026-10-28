package za.co.handyflow.platform.recruiter.domain.model;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;

/** The interview calendar's date range: whole days, South African time, at most six weeks at a time. */
public final class RecruiterCalendarRules {
    private RecruiterCalendarRules() {}

    public static final int MAX_DAYS = 42;

    /** Null when the range is usable. */
    public static String rangeProblem(LocalDate from, LocalDate to) {
        if (from == null || to == null) return "Choose the first and last day to show.";
        if (to.isBefore(from)) return "The last day cannot be before the first day.";
        if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_DAYS) return "Show at most " + MAX_DAYS + " days at a time.";
        return null;
    }

    /** The Monday of the week that holds the date. */
    public static LocalDate weekStart(LocalDate d) { return d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)); }

    /** Interviews that no longer stand: moved to another time, or called off. */
    public static boolean stands(String outcome) {
        return outcome == null || !(outcome.equals("RESCHEDULED") || outcome.equals("CANCELLED"));
    }
}
