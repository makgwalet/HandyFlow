package za.co.handyflow.platform.projects.application.internal.schedule;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Maps working days to consecutive integers (origin = 0) so all scheduling
 * arithmetic — durations, lags, floats — is plain int maths in WORKING days,
 * weekends and holidays already skipped.
 *
 * Starts are rolled FORWARD to the next working day, ends are rolled BACK to the
 * previous one (a task that "ends Saturday" really ends Friday).
 * Dates before the origin clamp to 0; callers choose the origin as the earliest
 * date they care about, so that never happens in practice.
 */
final class WorkDayIndex {

    private final SimpleWorkingCalendar cal;
    private final LocalDate origin;
    private final List<LocalDate> days = new ArrayList<>();

    WorkDayIndex(SimpleWorkingCalendar cal, LocalDate earliest) {
        this.cal = cal;
        this.origin = cal.rollForward(earliest);
        this.days.add(origin);
    }

    int indexOf(LocalDate d)    { return locate(cal.rollForward(d)); }

    int indexOfEnd(LocalDate d) { return locate(cal.rollBack(d)); }

    LocalDate dateOf(int idx) {
        if (idx < 0) throw new IllegalArgumentException("Working-day index cannot be negative: " + idx);
        while (days.size() <= idx) {
            days.add(cal.next(days.get(days.size() - 1)));
        }
        return days.get(idx);
    }

    private int locate(LocalDate workingDay) {
        if (workingDay.isBefore(origin)) return 0;
        while (days.get(days.size() - 1).isBefore(workingDay)) {
            days.add(cal.next(days.get(days.size() - 1)));
        }
        return Collections.binarySearch(days, workingDay);
    }
}