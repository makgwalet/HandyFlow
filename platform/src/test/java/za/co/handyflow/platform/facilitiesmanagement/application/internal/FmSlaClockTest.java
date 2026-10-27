package za.co.handyflow.platform.facilitiesmanagement.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.facilitiesmanagement.application.internal.FmSlaClock.Calendar;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Dates used: Mon 2 Nov 2026, Tue 3, Wed 4, Thu 5, Fri 6, Sat 7, Sun 8, Mon 9 (South African time, UTC+2 all year).
 * The default calendar is Mon-Fri 08:00-17:00.
 */
class FmSlaClockTest {

    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");
    private static final String BH = FmSlaClock.CLOCK_BUSINESS;
    private static final String H24 = FmSlaClock.CLOCK_24X7;
    private static final Calendar CAL = Calendar.defaults();

    private static Instant at(int day, int hour, int minute) {
        return ZonedDateTime.of(2026, 11, day, hour, minute, 0, 0, SAST).toInstant();
    }

    private static Calendar withHoliday(int day) {
        return new Calendar(CAL.start(), CAL.end(), CAL.workingDays(), CAL.zone(), Set.of(LocalDate.of(2026, 11, day)));
    }

    // ── addMinutes: 24x7 ─────────────────────────────────────────────────────

    @Test
    void aTwentyFourSevenClockIgnoresNightsAndWeekends() {
        assertEquals(at(8, 3, 30), FmSlaClock.addMinutes(at(7, 22, 0), 330, H24, CAL)); // Sat 22:00 + 5h30 = Sun 03:30
    }

    @Test
    void zeroMinutesDueImmediately() {
        assertEquals(at(2, 9, 0), FmSlaClock.addMinutes(at(2, 9, 0), 0, BH, CAL));
    }

    @Test
    void negativeMinutesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> FmSlaClock.addMinutes(at(2, 9, 0), -1, BH, CAL));
    }

    // ── addMinutes: business hours ───────────────────────────────────────────

    @Test
    void withinTheWorkingDayTimeJustAdds() {
        assertEquals(at(2, 10, 0), FmSlaClock.addMinutes(at(2, 9, 0), 60, BH, CAL));
    }

    @Test
    void aTargetThatEndsExactlyAtClosingIsDueAtClosing() {
        assertEquals(at(2, 17, 0), FmSlaClock.addMinutes(at(2, 16, 0), 60, BH, CAL));
    }

    @Test
    void timeRunsOutAtClosingAndContinuesNextMorning() {
        assertEquals(at(3, 8, 30), FmSlaClock.addMinutes(at(2, 16, 30), 60, BH, CAL)); // 30 min Mon + 30 min Tue
    }

    @Test
    void fridayAfternoonRollsOverTheWeekendToMonday() {
        assertEquals(at(9, 8, 30), FmSlaClock.addMinutes(at(6, 16, 30), 60, BH, CAL));
    }

    @Test
    void aJobLoggedOnSaturdayStartsTheClockOnMondayMorning() {
        assertEquals(at(9, 8, 30), FmSlaClock.addMinutes(at(7, 10, 0), 30, BH, CAL));
    }

    @Test
    void aJobLoggedBeforeOpeningStartsTheClockAtOpening() {
        assertEquals(at(2, 8, 30), FmSlaClock.addMinutes(at(2, 6, 0), 30, BH, CAL));
    }

    @Test
    void aJobLoggedAfterClosingStartsTheClockNextMorning() {
        assertEquals(at(3, 8, 30), FmSlaClock.addMinutes(at(2, 18, 0), 30, BH, CAL));
    }

    @Test
    void aPublicHolidayIsSkipped() {
        Calendar mondayHoliday = withHoliday(9);
        assertEquals(at(10, 8, 30), FmSlaClock.addMinutes(at(6, 16, 30), 60, BH, mondayHoliday)); // Fri 30 min + Tue 30 min
    }

    @Test
    void aLongTargetSpansSeveralDays() {
        // 3 working days of 9h = 1620 min, starting Mon 08:00 -> due Wed 17:00
        assertEquals(at(4, 17, 0), FmSlaClock.addMinutes(at(2, 8, 0), 1620, BH, CAL));
    }

    @Test
    void aCalendarWithNoWorkingDaysFailsLoudlyInsteadOfLoopingForever() {
        Calendar never = new Calendar(CAL.start(), CAL.end(), EnumSet.noneOf(DayOfWeek.class), CAL.zone(), Set.of());
        assertThrows(IllegalStateException.class, () -> FmSlaClock.addMinutes(at(2, 9, 0), 60, BH, never));
    }

    @Test
    void aSixDayWorkingWeekWorksOnSaturday() {
        Calendar sixDay = new Calendar(LocalTime.of(8, 0), LocalTime.of(13, 0),
                EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.SATURDAY), SAST, Set.of());
        assertEquals(at(7, 9, 0), FmSlaClock.addMinutes(at(7, 8, 0), 60, BH, sixDay));
        assertEquals(at(9, 8, 30), FmSlaClock.addMinutes(at(7, 12, 30), 60, BH, sixDay)); // 30 Sat + 30 Mon
    }

    // ── minutesBetween ───────────────────────────────────────────────────────

    @Test
    void measuresPlainElapsedTimeOnATwentyFourSevenClock() {
        assertEquals(1500, FmSlaClock.minutesBetween(at(2, 9, 0), at(3, 10, 0), H24, CAL));
    }

    @Test
    void measuresOnlyWorkingMinutesOnABusinessClock() {
        assertEquals(60, FmSlaClock.minutesBetween(at(2, 16, 30), at(3, 8, 30), BH, CAL));
        assertEquals(210, FmSlaClock.minutesBetween(at(2, 9, 0), at(2, 12, 30), BH, CAL));
    }

    @Test
    void aWeekendCountsForNothing() {
        assertEquals(120, FmSlaClock.minutesBetween(at(6, 16, 0), at(9, 9, 0), BH, CAL)); // Fri 60 + Mon 60
        assertEquals(0, FmSlaClock.minutesBetween(at(7, 9, 0), at(8, 17, 0), BH, CAL));
    }

    @Test
    void aHolidayCountsForNothing() {
        assertEquals(120, FmSlaClock.minutesBetween(at(6, 16, 0), at(10, 9, 0), BH, withHoliday(9))); // Fri 16:00-17:00 + Tue 08:00-09:00; the holiday Monday adds nothing
    }

    @Test
    void timeOutsideTheWorkingWindowIsClipped() {
        assertEquals(540, FmSlaClock.minutesBetween(at(2, 0, 0), at(2, 23, 59), BH, CAL)); // the whole 08:00-17:00 window
    }

    @Test
    void anIntervalThatIsNotPositiveMeasuresZero() {
        assertEquals(0, FmSlaClock.minutesBetween(at(2, 10, 0), at(2, 10, 0), BH, CAL));
        assertEquals(0, FmSlaClock.minutesBetween(at(2, 11, 0), at(2, 10, 0), BH, CAL));
        assertEquals(0, FmSlaClock.minutesBetween(at(2, 11, 0), at(2, 10, 0), H24, CAL));
    }

    // ── consistency ──────────────────────────────────────────────────────────

    @Test
    void addMinutesAndMinutesBetweenAreInverseOfEachOther() {
        for (int day = 2; day <= 9; day++) {
            for (int hour = 0; hour < 24; hour += 3) {
                for (long minutes : new long[]{1, 30, 45, 240, 540, 541, 1000, 4000}) {
                    Instant start = at(day, hour, 15);
                    Instant due = FmSlaClock.addMinutes(start, minutes, BH, CAL);
                    assertEquals(minutes, FmSlaClock.minutesBetween(start, due, BH, CAL),
                            "start day " + day + " hour " + hour + " minutes " + minutes);
                }
            }
        }
    }

    @Test
    void theDueTimeIsNeverBeforeTheStart() {
        for (int day = 2; day <= 9; day++) {
            Instant start = at(day, 12, 0);
            assertFalse(FmSlaClock.addMinutes(start, 1, BH, CAL).isBefore(start));
        }
    }

    @Test
    void clockNamesAreValidated() {
        assertTrue(FmSlaClock.isValidClock("24X7"));
        assertTrue(FmSlaClock.isValidClock("BUSINESS_HOURS"));
        assertFalse(FmSlaClock.isValidClock("office"));
        assertFalse(FmSlaClock.isValidClock(null));
    }
}
