package za.co.handyflow.platform.facilitiesmanagement.application.internal;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.EnumSet;
import java.util.Set;

/**
 * The SLA clock engine — pure functions, no database, so it is easy to test exhaustively.
 * <ul>
 *   <li>{@code 24X7}: plain calendar minutes.</li>
 *   <li>{@code BUSINESS_HOURS}: minutes only count inside the working window on working days, in the
 *       calendar's time zone, skipping weekends/non-working days and public holidays.</li>
 * </ul>
 * {@link #addMinutes} answers "when does a target of N minutes starting at T fall due?" and
 * {@link #minutesBetween} answers "how many SLA minutes passed between two instants?" (used to measure a pause).
 */
public final class FmSlaClock {

    public static final String CLOCK_24X7 = "24X7";
    public static final String CLOCK_BUSINESS = "BUSINESS_HOURS";

    private static final int MAX_DAYS = 3700; // ~10 years: a guard against calendars with no working time

    private FmSlaClock() {}

    /** Working window, working days, zone and holidays. */
    public record Calendar(LocalTime start, LocalTime end, Set<DayOfWeek> workingDays, ZoneId zone, Set<LocalDate> holidays) {

        /** Used when a tenant hasn't configured a calendar: Mon-Fri 08:00-17:00, South African time, no holidays. */
        public static Calendar defaults() {
            return new Calendar(LocalTime.of(8, 0), LocalTime.of(17, 0),
                    EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
                    ZoneId.of("Africa/Johannesburg"), Set.of());
        }

        boolean isWorkingDay(LocalDate day) {
            return workingDays.contains(day.getDayOfWeek()) && !holidays.contains(day);
        }
    }

    public static boolean isValidClock(String clock) {
        return CLOCK_24X7.equals(clock) || CLOCK_BUSINESS.equals(clock);
    }

    /** The instant at which {@code minutes} of SLA time, starting at {@code from}, have elapsed. */
    public static Instant addMinutes(Instant from, long minutes, String clock, Calendar cal) {
        if (minutes < 0) throw new IllegalArgumentException("minutes must not be negative");
        if (minutes == 0) return from;
        if (!CLOCK_BUSINESS.equals(clock)) return from.plus(Duration.ofMinutes(minutes));

        ZonedDateTime t = from.atZone(cal.zone());
        long remaining = minutes;
        for (int i = 0; i < MAX_DAYS && remaining > 0; i++) {
            LocalDate day = t.toLocalDate();
            if (!cal.isWorkingDay(day)) {
                t = day.plusDays(1).atStartOfDay(cal.zone());
                continue;
            }
            ZonedDateTime open = day.atTime(cal.start()).atZone(cal.zone());
            ZonedDateTime close = day.atTime(cal.end()).atZone(cal.zone());
            if (t.isBefore(open)) t = open;
            if (!t.isBefore(close)) {
                t = day.plusDays(1).atStartOfDay(cal.zone());
                continue;
            }
            long available = Duration.between(t, close).toMinutes();
            if (remaining <= available) return t.plusMinutes(remaining).toInstant();
            remaining -= available;
            t = day.plusDays(1).atStartOfDay(cal.zone());
        }
        throw new IllegalStateException("The business calendar has no working time within a reasonable horizon");
    }

    /** SLA minutes elapsed between two instants under the given clock. Zero when {@code to} is not after {@code from}. */
    public static long minutesBetween(Instant from, Instant to, String clock, Calendar cal) {
        if (!to.isAfter(from)) return 0;
        if (!CLOCK_BUSINESS.equals(clock)) return Duration.between(from, to).toMinutes();

        ZonedDateTime a = from.atZone(cal.zone());
        ZonedDateTime b = to.atZone(cal.zone());
        long total = 0;
        LocalDate day = a.toLocalDate();
        LocalDate last = b.toLocalDate();
        for (int i = 0; i < MAX_DAYS && !day.isAfter(last); i++, day = day.plusDays(1)) {
            if (!cal.isWorkingDay(day)) continue;
            ZonedDateTime open = day.atTime(cal.start()).atZone(cal.zone());
            ZonedDateTime close = day.atTime(cal.end()).atZone(cal.zone());
            ZonedDateTime s = a.isAfter(open) ? a : open;
            ZonedDateTime e = b.isBefore(close) ? b : close;
            if (e.isAfter(s)) total += Duration.between(s, e).toMinutes();
        }
        return total;
    }
}