package za.co.handyflow.platform.complianceservices.domain.model;

import java.time.LocalDate;
import java.util.*;

/**
 * What may be recorded as time worked. One entry is at most 12 hours and one person's day at most 16. Time cannot be recorded for a day that
 * has not happened, nor more than 60 days back unless an administrator does it. Rework and unplanned work are marked so they can be seen
 * apart from the work that was planned.
 */
public final class TimeRules {

    private TimeRules() {}

    public static final int MAX_ENTRY_MINUTES = 720;
    public static final int MAX_DAY_MINUTES = 960;
    public static final int MAX_BACKDATE_DAYS = 60;
    public static final int MIN_REASON = 10;
    public static final Set<String> KINDS = Set.of("PLANNED", "REWORK", "UNPLANNED");

    public record Totals(int minutes, int billableMinutes, int reworkMinutes, int unplannedMinutes) {
        public static Totals none() { return new Totals(0, 0, 0, 0); }
        public Totals add(Totals o) { return new Totals(minutes + o.minutes, billableMinutes + o.billableMinutes, reworkMinutes + o.reworkMinutes, unplannedMinutes + o.unplannedMinutes); }
        public Totals plus(int mins, boolean billable, String kind) {
            return new Totals(minutes + mins, billableMinutes + (billable ? mins : 0), reworkMinutes + ("REWORK".equals(kind) ? mins : 0), unplannedMinutes + ("UNPLANNED".equals(kind) ? mins : 0));
        }
    }

    public static String kind(String raw) {
        String k = raw == null || raw.isBlank() ? "PLANNED" : raw.trim().toUpperCase(Locale.ROOT);
        if (!KINDS.contains(k)) throw new IllegalArgumentException("The kind of work must be planned, rework or unplanned");
        return k;
    }

    public static String serviceCode(String raw) {
        String t = raw == null ? "" : raw.trim();
        if (t.isEmpty()) return null;
        if (t.length() > 60) throw new IllegalArgumentException("The service code is too long");
        return t;
    }

    public static String note(String raw) {
        String t = raw == null ? "" : raw.trim();
        if (t.isEmpty()) return null;
        if (t.length() > 1000) throw new IllegalArgumentException("The note is too long (at most 1000 characters)");
        return t;
    }

    public static String reason(String raw) {
        String t = raw == null ? "" : raw.trim();
        if (t.length() < MIN_REASON) throw new IllegalArgumentException("Say why, in at least " + MIN_REASON + " characters");
        if (t.length() > 4000) throw new IllegalArgumentException("The reason is too long");
        return t;
    }

    /** Checks one new entry against the day it is for and what that person has already recorded for that day. */
    public static void check(int minutes, LocalDate workDate, LocalDate today, boolean admin, int alreadyThatDay) {
        if (minutes < 1 || minutes > MAX_ENTRY_MINUTES) throw new IllegalArgumentException("One entry is between 1 minute and 12 hours");
        if (workDate == null) throw new IllegalArgumentException("Say which day the work was done");
        if (workDate.isAfter(today)) throw new IllegalArgumentException("Time cannot be recorded for a day that has not happened yet");
        if (!admin && workDate.isBefore(today.minusDays(MAX_BACKDATE_DAYS))) throw new IllegalArgumentException("That day is more than " + MAX_BACKDATE_DAYS + " days ago. Ask an administrator to record it");
        if (alreadyThatDay + minutes > MAX_DAY_MINUTES) throw new IllegalArgumentException("That would make more than 16 hours for one day");
    }

    /** "1h 30m", "45m", "2h". */
    public static String hours(int minutes) {
        int h = minutes / 60, m = minutes % 60;
        return h == 0 ? m + "m" : m == 0 ? h + "h" : h + "h " + m + "m";
    }
}
