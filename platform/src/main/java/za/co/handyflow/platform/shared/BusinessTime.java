// shared/BusinessTime.java
package za.co.handyflow.platform.shared;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;

/**
 * Pure rules for business time: moments are stored in UTC, but the day, week or month a moment belongs to is decided in a zone
 * (the company's). Every range is half-open [from, to) in instants. Nothing here reads the server's own time zone.
 */
public final class BusinessTime {

    public static final ZoneId DEFAULT_ZONE = ZoneId.of("Africa/Johannesburg");
    private static final String[] MONTHS = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};

    private BusinessTime() {}

    /** The zone for an IANA id, or the default when it is missing or not a known zone. */
    public static ZoneId zoneOrDefault(String id) {
        if (id == null || id.isBlank()) return DEFAULT_ZONE;
        try { return ZoneId.of(id.trim()); } catch (java.time.DateTimeException e) { return DEFAULT_ZONE; }
    }

    public static boolean validZone(String id) {
        if (id == null || id.isBlank()) return false;
        try { ZoneId.of(id.trim()); return true; } catch (java.time.DateTimeException e) { return false; }
    }

    public static LocalDate businessDateOf(ZoneId zone, Instant moment) { return moment.atZone(zone).toLocalDate(); }

    public static LocalDate today(ZoneId zone, Instant now) { return businessDateOf(zone, now); }

    /** Local midnight to the next local midnight. A day with a clock change is 23 or 25 hours long, not 24. */
    public static Instant[] dayRange(ZoneId zone, LocalDate day) {
        return new Instant[]{day.atStartOfDay(zone).toInstant(), day.plusDays(1).atStartOfDay(zone).toInstant()};
    }

    /** Monday 00:00 to the next Monday 00:00 of the week containing the day. */
    public static Instant[] weekRange(ZoneId zone, LocalDate anyDay) {
        LocalDate monday = anyDay.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return new Instant[]{monday.atStartOfDay(zone).toInstant(), monday.plusDays(7).atStartOfDay(zone).toInstant()};
    }

    public static Instant[] monthRange(ZoneId zone, YearMonth month) {
        return new Instant[]{month.atDay(1).atStartOfDay(zone).toInstant(), month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant()};
    }

    /** "22 Nov 2026 09:47 SAST": always says which zone the time is in. */
    public static String format(ZoneId zone, Instant moment) {
        ZonedDateTime z = moment.atZone(zone);
        return z.getDayOfMonth() + " " + MONTHS[z.getMonthValue() - 1] + " " + z.getYear() + " "
                + DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH).format(z) + " " + abbreviation(zone, moment);
    }

    /** The short zone name at that moment (SAST, UTC, ...). */
    public static String abbreviation(ZoneId zone, Instant moment) {
        return DateTimeFormatter.ofPattern("zzz", Locale.ENGLISH).format(moment.atZone(zone));
    }
}
