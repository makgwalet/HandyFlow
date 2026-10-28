package za.co.handyflow.platform.hr.domain.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class SavedReportRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 14); // a Wednesday

    @Test
    void settingsAreChecked() {
        assertNull(SavedReportRules.problem("Monthly cost", "PAYROLL", "LAST_MONTH", "MONTHLY", 3));
        assertNull(SavedReportRules.problem("Headcount", "WORKFORCE", "YEAR_TO_DATE", null, null));
        assertNotNull(SavedReportRules.problem(" ", "WORKFORCE", "YEAR_TO_DATE", "NONE", null));
        assertNotNull(SavedReportRules.problem("x".repeat(81), "WORKFORCE", "YEAR_TO_DATE", "NONE", null));
        assertNotNull(SavedReportRules.problem("A", "SALARIES", "YEAR_TO_DATE", "NONE", null));
        assertNotNull(SavedReportRules.problem("A", "TIME", "LAST_WEEK", "NONE", null));
        assertNotNull(SavedReportRules.problem("A", "TIME", "LAST_MONTH", "DAILY", 1));
        assertNotNull(SavedReportRules.problem("A", "TIME", "LAST_MONTH", "WEEKLY", null));
        assertNotNull(SavedReportRules.problem("A", "TIME", "LAST_MONTH", "WEEKLY", 8));
        assertNotNull(SavedReportRules.problem("A", "TIME", "LAST_MONTH", "MONTHLY", 29));
        assertNotNull(SavedReportRules.problem("A", "TIME", "LAST_MONTH", "NONE", 3));
    }

    @Test
    void onlyPeopleWhoSeePayMayKeepAPayrollReport() {
        assertFalse(SavedReportRules.mayKeep("PAYROLL", false));
        assertTrue(SavedReportRules.mayKeep("PAYROLL", true));
        assertTrue(SavedReportRules.mayKeep("WORKFORCE", false));
    }

    @Test
    void periodsMeanTheRightDates() {
        assertArrayEquals(new LocalDate[]{LocalDate.of(2026, 10, 1), TODAY}, SavedReportRules.resolve("THIS_MONTH", TODAY));
        assertArrayEquals(new LocalDate[]{LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)}, SavedReportRules.resolve("LAST_MONTH", TODAY));
        assertArrayEquals(new LocalDate[]{LocalDate.of(2026, 10, 1), TODAY}, SavedReportRules.resolve("THIS_QUARTER", TODAY));
        assertArrayEquals(new LocalDate[]{LocalDate.of(2026, 7, 1), LocalDate.of(2026, 9, 30)}, SavedReportRules.resolve("LAST_QUARTER", TODAY));
        assertArrayEquals(new LocalDate[]{LocalDate.of(2026, 1, 1), TODAY}, SavedReportRules.resolve("YEAR_TO_DATE", TODAY));
        assertArrayEquals(new LocalDate[]{TODAY.minusDays(89), TODAY}, SavedReportRules.resolve("LAST_90_DAYS", TODAY));
        assertArrayEquals(new LocalDate[]{TODAY.minusDays(364), TODAY}, SavedReportRules.resolve("LAST_12_MONTHS", TODAY));
    }

    @Test
    void januaryLooksBackIntoTheYearBefore() {
        LocalDate jan = LocalDate.of(2027, 1, 12);
        assertArrayEquals(new LocalDate[]{LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 31)}, SavedReportRules.resolve("LAST_MONTH", jan));
        assertArrayEquals(new LocalDate[]{LocalDate.of(2026, 10, 1), LocalDate.of(2026, 12, 31)}, SavedReportRules.resolve("LAST_QUARTER", jan));
    }

    @Test
    void everyAllowedPeriodResolvesToAValidRangeOfAtMost366Days() {
        for (String p : SavedReportRules.PERIODS) {
            LocalDate[] r = SavedReportRules.resolve(p, TODAY);
            assertFalse(r[1].isBefore(r[0]), p);
            assertTrue(java.time.temporal.ChronoUnit.DAYS.between(r[0], r[1]) + 1 <= 366, p);
        }
    }

    @Test
    void aWeeklyReminderFallsOnItsWeekdayOnly() {
        assertTrue(SavedReportRules.dueToday("WEEKLY", 3, null, TODAY));
        assertFalse(SavedReportRules.dueToday("WEEKLY", 2, null, TODAY));
    }

    @Test
    void aMonthlyReminderFallsOnItsDayOnly() {
        assertTrue(SavedReportRules.dueToday("MONTHLY", 14, LocalDate.of(2026, 9, 14), TODAY));
        assertFalse(SavedReportRules.dueToday("MONTHLY", 15, null, TODAY));
    }

    @Test
    void aReminderIsSentOncePerDay() {
        assertFalse(SavedReportRules.dueToday("WEEKLY", 3, TODAY, TODAY));
        assertTrue(SavedReportRules.dueToday("WEEKLY", 3, TODAY.minusDays(7), TODAY));
    }

    @Test
    void noReminderMeansNeverDue() {
        assertFalse(SavedReportRules.dueToday("NONE", null, null, TODAY));
        assertFalse(SavedReportRules.dueToday(null, null, null, TODAY));
        assertFalse(SavedReportRules.dueToday("WEEKLY", null, null, TODAY));
    }

    @Test
    void theReminderNamesTheReportAndDatesButHoldsNoFigures() {
        String m = SavedReportRules.reminderMessage("PAYROLL", "LAST_MONTH", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
        assertTrue(m.contains("2026-09-01") && m.contains("2026-09-30") && m.contains("payroll"));
        assertFalse(m.matches(".*R\\s?\\d.*"));
    }
}
