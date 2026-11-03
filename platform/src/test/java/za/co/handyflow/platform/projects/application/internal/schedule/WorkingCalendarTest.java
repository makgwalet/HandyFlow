package za.co.handyflow.platform.projects.application.internal.schedule;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** 2026-11-02 is a Monday. */
class WorkingCalendarTest {

    static LocalDate d(String s) { return LocalDate.parse(s); }
    static final SimpleWorkingCalendar CAL = SimpleWorkingCalendar.weekdays();

    @Test
    void plusWorkingDays_skipsWeekends() {
        assertEquals(d("2026-11-09"), CAL.plusWorkingDays(d("2026-11-06"), 1));   // Fri + 1 = Mon
        assertEquals(d("2026-11-10"), CAL.plusWorkingDays(d("2026-11-05"), 3));   // Thu + 3 = Tue
        assertEquals(d("2026-11-06"), CAL.plusWorkingDays(d("2026-11-06"), 0));
    }

    @Test
    void plusWorkingDays_rollsAWeekendStartForwardFirst() {
        assertEquals(d("2026-11-09"), CAL.plusWorkingDays(d("2026-11-07"), 0));   // Sat → Mon
        assertEquals(d("2026-11-10"), CAL.plusWorkingDays(d("2026-11-07"), 1));
    }

    @Test
    void extendingATaskEnd_whichFallsOnAWeekend_countsFromTheLastWorkingDay() {
        // End on Saturday really ends Friday; one extra working day → Monday.
        assertEquals(d("2026-11-09"), CAL.plusWorkingDays(CAL.rollBack(d("2026-11-07")), 1));
    }

    @Test
    void plusWorkingDays_skipsHolidays() {
        Set<DayOfWeek> weekdays = EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);
        SimpleWorkingCalendar withHoliday = new SimpleWorkingCalendar(weekdays, Set.of(d("2026-11-04")));
        assertEquals(d("2026-11-05"), withHoliday.plusWorkingDays(d("2026-11-03"), 1));   // Tue + 1 skips Wed holiday
    }

    @Test
    void negativeDays_areRejected() {
        try {
            CAL.plusWorkingDays(d("2026-11-02"), -1);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { /* ok */ }
    }
}