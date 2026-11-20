package za.co.handyflow.platform.legalpractice.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.legalpractice.application.internal.DeadlineCalculator.*;

import java.time.LocalDate;
import java.time.MonthDay;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DeadlineCalculatorTest {

    private static final MonthDay R_START = MonthDay.of(12, 16), R_END = MonthDay.of(1, 15);

    /** The statutory holidays plus whatever closures a test adds. */
    private static Calendar cal(Map<LocalDate, String> closures) {
        return new Calendar() {
            public String publicHoliday(LocalDate d) { return SaPublicHolidays.nameOf(d); }
            public String courtClosure(LocalDate d) { return closures.get(d); }
        };
    }
    private static Calendar cal() { return cal(Map.of()); }

    private static Rule rule(int n, Unit u, Direction dir, Rollover ro, boolean recess) {
        return new Rule(n, u, dir, ro, recess ? R_START : null, recess ? R_END : null);
    }
    private static LocalDate d(int y, int m, int day) { return LocalDate.of(y, m, day); }
    private static LocalDate run(LocalDate t, Rule r) { return DeadlineCalculator.compute(t, r, cal()).date(); }

    // ── public holidays ─────────────────────────────────────────────────────

    @Test
    void easterIsRightForKnownYears() {
        assertEquals(d(2024, 3, 31), SaPublicHolidays.easterSunday(2024));
        assertEquals(d(2025, 4, 20), SaPublicHolidays.easterSunday(2025));
        assertEquals(d(2026, 4, 5), SaPublicHolidays.easterSunday(2026));
        assertEquals(d(2027, 3, 28), SaPublicHolidays.easterSunday(2027));
    }

    @Test
    void movableHolidaysFollowEaster() {
        assertEquals("Good Friday", SaPublicHolidays.nameOf(d(2026, 4, 3)));
        assertEquals("Family Day", SaPublicHolidays.nameOf(d(2026, 4, 6)));
        assertEquals("Good Friday", SaPublicHolidays.nameOf(d(2027, 3, 26)));
        assertEquals("Family Day", SaPublicHolidays.nameOf(d(2027, 3, 29)));
    }

    @Test
    void aSundayHolidayMovesToTheMondayButASaturdayDoesNot() {
        assertNotNull(SaPublicHolidays.nameOf(d(2026, 8, 9)), "9 Aug 2026 is a Sunday and is still the holiday");
        assertEquals("National Women's Day (observed)", SaPublicHolidays.nameOf(d(2026, 8, 10)));
        assertEquals("Human Rights Day (observed)", SaPublicHolidays.nameOf(d(2027, 3, 22)));
        assertEquals("Day of Goodwill (observed)", SaPublicHolidays.nameOf(d(2027, 12, 27)));
        assertNull(SaPublicHolidays.nameOf(d(2026, 3, 23)), "21 Mar 2026 is a Saturday: no Monday holiday");
        assertNull(SaPublicHolidays.nameOf(d(2027, 12, 28)), "25 Dec 2027 is a Saturday: no extra day after Goodwill");
    }

    @Test
    void anOrdinaryDayIsNotAHolidayAndYearsOutsideTheRangeAreRefusedForLookups() {
        assertNull(SaPublicHolidays.nameOf(d(2026, 11, 20)));
        assertNull(SaPublicHolidays.nameOf(d(1990, 1, 1)), "before the Act: not treated as a holiday");
        assertThrows(IllegalArgumentException.class, () -> SaPublicHolidays.forYear(1990));
    }

    // ── court days ──────────────────────────────────────────────────────────

    @Test
    void tenCourtDaysSkipOnlyWeekends() {
        assertEquals(d(2026, 3, 20), run(d(2026, 3, 6), rule(10, Unit.COURT_DAYS, Direction.AFTER, Rollover.NONE, false)));
    }

    @Test
    void courtDaysSkipHolidaysAroundEaster() {
        assertEquals(d(2026, 4, 8), run(d(2026, 4, 1), rule(3, Unit.COURT_DAYS, Direction.AFTER, Rollover.NONE, false)));
    }

    @Test
    void theTriggerDayIsNeverCountedEvenWhenItIsNotACourtDay() {
        // Served on Saturday 4 April 2026: day 1 is Tuesday 7 April (Good Friday, weekend and Family Day are all skipped).
        assertEquals(d(2026, 4, 7), run(d(2026, 4, 4), rule(1, Unit.COURT_DAYS, Direction.AFTER, Rollover.NONE, false)));
    }

    @Test
    void recessIsNotCounted() {
        assertEquals(d(2027, 1, 26), run(d(2026, 12, 10), rule(10, Unit.COURT_DAYS, Direction.AFTER, Rollover.NONE, true)));
    }

    @Test
    void withoutARecessWindowDecemberCountsExceptHolidays() {
        assertEquals(d(2026, 12, 28), run(d(2026, 12, 10), rule(10, Unit.COURT_DAYS, Direction.AFTER, Rollover.NONE, false)));
    }

    @Test
    void courtDaysCountedBackwardsFromAHearing() {
        assertEquals(d(2026, 3, 27), run(d(2026, 4, 7), rule(5, Unit.COURT_DAYS, Direction.BEFORE, Rollover.NONE, false)));
    }

    @Test
    void aCourtClosureIsSkippedButOnlyForCourtDays() {
        Map<LocalDate, String> closed = Map.of(d(2026, 3, 11), "Floods: court closed");
        Rule courtDays = rule(3, Unit.COURT_DAYS, Direction.AFTER, Rollover.NONE, false);
        assertEquals(d(2026, 3, 13), DeadlineCalculator.compute(d(2026, 3, 9), courtDays, cal(closed)).date(), "10, (11 closed), 12, 13");
        Rule calendar = rule(2, Unit.CALENDAR_DAYS, Direction.AFTER, Rollover.NONE, false);
        assertEquals(d(2026, 3, 11), DeadlineCalculator.compute(d(2026, 3, 9), calendar, cal(closed)).date(), "calendar days still count it");
    }

    // ── calendar days, months, years ────────────────────────────────────────

    @Test
    void calendarDaysCountWeekendsAndHolidays() {
        assertEquals(d(2026, 8, 9), run(d(2026, 7, 10), rule(30, Unit.CALENDAR_DAYS, Direction.AFTER, Rollover.NONE, false)));
    }

    @Test
    void sundayAndHolidayRolloverMovesForwardPastTheObservedHolidayToo() {
        assertEquals(d(2026, 8, 11), run(d(2026, 7, 10), rule(30, Unit.CALENDAR_DAYS, Direction.AFTER, Rollover.SKIP_SUNDAY_AND_HOLIDAYS, false)));
    }

    @Test
    void sundayRolloverLeavesASaturdayAlone() {
        assertEquals(d(2026, 8, 8), run(d(2026, 7, 9), rule(30, Unit.CALENDAR_DAYS, Direction.AFTER, Rollover.SKIP_SUNDAY_AND_HOLIDAYS, false)));
        assertEquals(d(2026, 8, 11), run(d(2026, 7, 9), rule(30, Unit.CALENDAR_DAYS, Direction.AFTER, Rollover.SKIP_NON_COURT_DAYS, false)),
                "the court-day rollover goes on past the Saturday, Sunday and the observed holiday");
    }

    @Test
    void aPeriodBeforeAnEventRollsBackNeverForward() {
        Rule interp = rule(10, Unit.CALENDAR_DAYS, Direction.BEFORE, Rollover.SKIP_SUNDAY_AND_HOLIDAYS, false);
        Rule court = rule(10, Unit.CALENDAR_DAYS, Direction.BEFORE, Rollover.SKIP_NON_COURT_DAYS, false);
        assertEquals(d(2026, 8, 8), run(d(2026, 8, 20), interp));
        assertEquals(d(2026, 8, 7), run(d(2026, 8, 20), court));
    }

    @Test
    void calendarDaysDoNotCountRecessDays() {
        assertEquals(d(2027, 1, 20), run(d(2026, 12, 10), rule(10, Unit.CALENDAR_DAYS, Direction.AFTER, Rollover.NONE, true)));
    }

    @Test
    void monthsClampToTheEndOfAShorterMonth() {
        assertEquals(d(2027, 2, 28), run(d(2026, 8, 31), rule(6, Unit.MONTHS, Direction.AFTER, Rollover.NONE, false)));
        assertEquals(d(2027, 3, 1), run(d(2026, 8, 31), rule(6, Unit.MONTHS, Direction.AFTER, Rollover.SKIP_SUNDAY_AND_HOLIDAYS, false)), "28 Feb 2027 is a Sunday");
    }

    @Test
    void yearsForPrescriptionLandOnTheSameDateAndLeapDayClamps() {
        assertEquals(d(2026, 11, 20), run(d(2023, 11, 20), rule(3, Unit.YEARS, Direction.AFTER, Rollover.NONE, false)));
        assertEquals(d(2025, 2, 28), run(d(2024, 2, 29), rule(1, Unit.YEARS, Direction.AFTER, Rollover.NONE, false)));
    }

    @Test
    void monthAndYearPeriodsIgnoreTheRecessWindow() {
        assertEquals(d(2027, 6, 10), run(d(2026, 12, 10), rule(6, Unit.MONTHS, Direction.AFTER, Rollover.NONE, true)));
    }

    // ── explanation, validation, safety ─────────────────────────────────────

    @Test
    void theExplanationNamesEverySkippedDayAndCollapsesTheRecess() {
        var r = DeadlineCalculator.compute(d(2026, 12, 10), rule(10, Unit.COURT_DAYS, Direction.AFTER, Rollover.NONE, true), cal());
        var lines = r.explain();
        assertEquals(1L, lines.stream().filter(l -> l.contains("court recess (")).count(), "one line for the whole recess: " + lines);
        assertTrue(lines.stream().anyMatch(l -> l.contains("Wed 16 Dec 2026 to Fri 15 Jan 2027") && l.contains("(31 days)")), lines.toString());
        var easter = DeadlineCalculator.compute(d(2026, 4, 1), rule(3, Unit.COURT_DAYS, Direction.AFTER, Rollover.NONE, false), cal()).explain();
        assertTrue(easter.contains("Fri 3 Apr 2026: public holiday (Good Friday)"), easter.toString());
        assertTrue(easter.contains("Sat 4 Apr 2026: weekend"), easter.toString());
        assertTrue(easter.contains("Mon 6 Apr 2026: public holiday (Family Day)"), easter.toString());
    }

    @Test
    void aClosureIsExplainedWithItsReason() {
        var r = DeadlineCalculator.compute(d(2026, 3, 9), rule(1, Unit.COURT_DAYS, Direction.AFTER, Rollover.NONE, false),
                cal(Map.of(d(2026, 3, 10), "Water outage")));
        assertEquals(d(2026, 3, 11), r.date());
        assertEquals(java.util.List.of("Tue 10 Mar 2026: court closed (Water outage)"), r.explain());
    }

    @Test
    void badRulesAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> run(d(2026, 1, 5), rule(0, Unit.COURT_DAYS, Direction.AFTER, Rollover.NONE, false)));
        assertThrows(IllegalArgumentException.class, () -> run(d(2026, 1, 5), rule(3661, Unit.CALENDAR_DAYS, Direction.AFTER, Rollover.NONE, false)));
        assertThrows(IllegalArgumentException.class, () -> run(d(2026, 1, 5), rule(361, Unit.MONTHS, Direction.AFTER, Rollover.NONE, false)));
        assertThrows(IllegalArgumentException.class, () -> run(d(2026, 1, 5), rule(31, Unit.YEARS, Direction.AFTER, Rollover.NONE, false)));
        assertThrows(IllegalArgumentException.class, () -> run(d(2026, 1, 5), new Rule(5, Unit.COURT_DAYS, Direction.AFTER, Rollover.NONE, R_START, null)));
        assertThrows(IllegalArgumentException.class, () -> DeadlineCalculator.compute(null, rule(5, Unit.COURT_DAYS, Direction.AFTER, Rollover.NONE, false), cal()));
    }

    @Test
    void aCalendarThatClosesEveryDayEndsInAnErrorNotAnEndlessLoop() {
        Calendar always = new Calendar() {
            public String publicHoliday(LocalDate d) { return "closed"; }
            public String courtClosure(LocalDate d) { return null; }
        };
        assertThrows(IllegalStateException.class, () -> DeadlineCalculator.compute(d(2026, 1, 5),
                rule(5, Unit.COURT_DAYS, Direction.AFTER, Rollover.NONE, false), always));
    }
}
