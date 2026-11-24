package za.co.handyflow.platform.contracting.domain.model;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.Set;

/**
 * The clock an approval waits on. In {@code BUSINESS_HOURS} mode time only counts inside the company's working hours, on working days that
 * are not public holidays or closure days (the caller passes those dates in, so this class needs no database). In {@code CALENDAR} mode it is
 * plain elapsed time. Pure rules: nothing here reads the server's clock or time zone.
 */
public final class ApprovalWorkingTime {

    public static final Set<DayOfWeek> MON_TO_FRI = Set.copyOf(EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY));
    /** Longest stretch searched for working time before giving up (about ten years). */
    private static final int MAX_DAYS = 3660;

    private final ZoneId zone;
    private final LocalTime start;
    private final LocalTime end;
    private final Set<DayOfWeek> days;
    private final Set<LocalDate> closed;
    private final boolean continuous;

    private ApprovalWorkingTime(ZoneId zone, LocalTime start, LocalTime end, Set<DayOfWeek> days, Set<LocalDate> closed, boolean continuous) {
        this.zone = zone; this.start = start; this.end = end; this.days = days; this.closed = closed; this.continuous = continuous;
    }

    /** Plain elapsed time: nights, weekends and holidays all count. */
    public static ApprovalWorkingTime calendar(ZoneId zone) {
        return new ApprovalWorkingTime(zone, LocalTime.MIDNIGHT, LocalTime.MAX, MON_TO_FRI, Set.of(), true);
    }

    /** Time that counts only from {@code start} to {@code end} on the listed days, never on a closed date. */
    public static ApprovalWorkingTime businessHours(ZoneId zone, LocalTime start, LocalTime end, Set<DayOfWeek> days, Set<LocalDate> closedDates) {
        if (zone == null || start == null || end == null) throw new IllegalArgumentException("Working hours need a zone, a start and an end");
        if (!start.isBefore(end)) throw new IllegalArgumentException("Working hours must start before they end");
        if (days == null || days.isEmpty()) throw new IllegalArgumentException("Choose at least one working day");
        return new ApprovalWorkingTime(zone, start, end, Set.copyOf(days), closedDates == null ? Set.of() : Set.copyOf(closedDates), false);
    }

    public boolean isContinuous() { return continuous; }
    public ZoneId zone() { return zone; }

    public boolean isWorkingDay(LocalDate d) {
        return continuous || (days.contains(d.getDayOfWeek()) && !closed.contains(d));
    }

    /** Whole minutes of waiting between two moments (zero when {@code to} is not after {@code from}). */
    public long minutesBetween(Instant from, Instant to) {
        if (from == null || to == null || !to.isAfter(from)) return 0;
        if (continuous) return Duration.between(from, to).toMinutes();
        long seconds = 0;
        LocalDate last = to.atZone(zone).toLocalDate();
        for (LocalDate d = from.atZone(zone).toLocalDate(); !d.isAfter(last); d = d.plusDays(1)) {
            if (!isWorkingDay(d)) continue;
            Instant open = opens(d), close = closes(d);
            Instant s = from.isAfter(open) ? from : open;
            Instant e = to.isBefore(close) ? to : close;
            if (e.isAfter(s)) seconds += Duration.between(s, e).getSeconds();
        }
        return seconds / 60;
    }

    /**
     * The moment {@code minutes} of waiting will have passed, counting from {@code from}. Used to show when the next reminder is due.
     * @throws IllegalStateException when there is no working time to be found (every day closed)
     */
    public Instant addMinutes(Instant from, long minutes) {
        if (minutes <= 0) return from;
        if (continuous) return from.plusSeconds(minutes * 60);
        long remaining = minutes * 60;
        LocalDate d = from.atZone(zone).toLocalDate();
        for (int i = 0; i < MAX_DAYS; i++, d = d.plusDays(1)) {
            if (!isWorkingDay(d)) continue;
            Instant open = opens(d), close = closes(d);
            Instant s = from.isAfter(open) ? from : open;
            if (!close.isAfter(s)) continue;
            long available = Duration.between(s, close).getSeconds();
            if (remaining <= available) return s.plusSeconds(remaining);
            remaining -= available;
        }
        throw new IllegalStateException("No working time found within ten years");
    }

    private Instant opens(LocalDate d) { return d.atTime(start).atZone(zone).toInstant(); }
    private Instant closes(LocalDate d) { return d.atTime(end).atZone(zone).toInstant(); }
}
