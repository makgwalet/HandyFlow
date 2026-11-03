package za.co.handyflow.platform.projects.application.internal.schedule;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;

/**
 * Which days count as working days: a set of weekdays minus a set of holidays.
 * Pure and immutable — no Spring, no database — so the scheduling engine can be
 * unit-tested with any calendar.
 *
 * Holidays only apply inside the range the caller loaded them for; beyond it the
 * calendar silently falls back to weekdays-only.
 */
public final class SimpleWorkingCalendar {

    private static final int MAX_SCAN_DAYS = 3660;   // guards against a calendar with no usable days

    private final Set<DayOfWeek> workingDays;
    private final Set<LocalDate> holidays;

    public SimpleWorkingCalendar(Set<DayOfWeek> workingDays, Set<LocalDate> holidays) {
        if (workingDays == null || workingDays.isEmpty()) {
            throw new IllegalArgumentException("A working calendar needs at least one working day of the week");
        }
        this.workingDays = EnumSet.copyOf(workingDays);
        this.holidays = holidays == null ? Set.of() : Set.copyOf(holidays);
    }

    /** Monday–Friday, no holidays. */
    public static SimpleWorkingCalendar weekdays() {
        return new SimpleWorkingCalendar(
                EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                        DayOfWeek.THURSDAY, DayOfWeek.FRIDAY), Set.of());
    }

    public boolean isWorking(LocalDate d) {
        return workingDays.contains(d.getDayOfWeek()) && !holidays.contains(d);
    }

    /** d itself if it is a working day, otherwise the next one. */
    public LocalDate rollForward(LocalDate d) {
        LocalDate x = d;
        for (int i = 0; i < MAX_SCAN_DAYS; i++) {
            if (isWorking(x)) return x;
            x = x.plusDays(1);
        }
        throw new IllegalStateException("No working day found within " + MAX_SCAN_DAYS + " days of " + d);
    }

    /** d itself if it is a working day, otherwise the previous one. */
    public LocalDate rollBack(LocalDate d) {
        LocalDate x = d;
        for (int i = 0; i < MAX_SCAN_DAYS; i++) {
            if (isWorking(x)) return x;
            x = x.minusDays(1);
        }
        throw new IllegalStateException("No working day found within " + MAX_SCAN_DAYS + " days before " + d);
    }

    /** The working day strictly after d. */
    public LocalDate next(LocalDate d) {
        return rollForward(d.plusDays(1));
    }

    /**
     * CHUNK 5: d (rolled forward to a working day) plus n further working days. n must be >= 0.
     * To extend a task END date by n working days, call plusWorkingDays(rollBack(end), n).
     */
    public LocalDate plusWorkingDays(LocalDate d, int n) {
        if (n < 0) throw new IllegalArgumentException("Working days to add cannot be negative: " + n);
        LocalDate x = rollForward(d);
        for (int i = 0; i < n; i++) x = next(x);
        return x;
    }
}