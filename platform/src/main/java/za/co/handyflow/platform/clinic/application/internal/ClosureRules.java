package za.co.handyflow.platform.clinic.application.internal;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** Pure rules for whole-day clinic closures (public holidays, staff day, maintenance). */
final class ClosureRules {

    static final int MAX_DAYS = 60;
    static final int MAX_REASON = 200;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH);

    /** A closure that overlaps a proposed booking. */
    record Closure(LocalDate from, LocalDate to, String reason) {}

    private ClosureRules() {}

    /** Returns the cleaned reason (null when blank) or throws IllegalArgumentException for an unusable closure. */
    static String validate(LocalDate from, LocalDate to, String reason) {
        if (from == null || to == null) throw new IllegalArgumentException("First and last day are required");
        if (to.isBefore(from)) throw new IllegalArgumentException("The last day cannot be before the first day");
        long days = to.toEpochDay() - from.toEpochDay() + 1;
        if (days > MAX_DAYS) throw new IllegalArgumentException("A closure can be at most " + MAX_DAYS + " days");
        String r = reason == null ? null : reason.trim();
        if (r != null && r.isEmpty()) r = null;
        if (r != null && r.length() > MAX_REASON) throw new IllegalArgumentException("The reason is too long (at most " + MAX_REASON + " characters)");
        return r;
    }

    /** First and last clinic-time calendar day a booking touches. A booking ending exactly at midnight does not touch the next day. */
    static LocalDate[] daysTouched(Instant start, int minutes, ZoneId zone) {
        LocalDate first = start.atZone(zone).toLocalDate();
        LocalDate last = start.plusSeconds(minutes * 60L - 1).atZone(zone).toLocalDate();
        return new LocalDate[]{first, last};
    }

    static String message(List<Closure> closures, ZoneId zone) {
        Closure c = closures.get(0);
        String span = c.from().equals(c.to()) ? c.from().format(DAY) : c.from().format(DAY) + " to " + c.to().format(DAY);
        String why = c.reason() == null ? "" : " (" + c.reason() + ")";
        String more = closures.size() > 1 ? " and " + (closures.size() - 1) + " more closure(s)" : "";
        return "The clinic is closed " + span + why + more + ".";
    }
}
