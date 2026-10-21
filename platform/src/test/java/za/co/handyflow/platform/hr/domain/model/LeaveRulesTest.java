package za.co.handyflow.platform.hr.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class LeaveRulesTest {

    private static LocalDate d(int y, int m, int day) { return LocalDate.of(y, m, day); }
    private static final LocalDate TODAY = d(2026, 10, 21);
    private static final LocalDate STARTED = d(2024, 3, 1);

    @Test
    void workingDaysSkipWeekendsAndHolidays() {
        // Mon 2026-09-21 to Sun 2026-09-27 with Thursday 24 September (Heritage Day) a holiday
        List<LocalDate> days = LeaveRules.workingDays(d(2026, 9, 21), d(2026, 9, 27), Set.of(d(2026, 9, 24)));
        assertThat(days.size()).isEqualTo(4);
        assertThat(days.contains(d(2026, 9, 24))).isFalse();
    }

    @Test
    void weekdaysCountHolidaysAsWorked() {
        assertThat(LeaveRules.weekdays(d(2026, 9, 21), d(2026, 9, 27))).isEqualTo(5);
    }

    @Test
    void daysAcrossAYearEndAreChargedToBothYears() {
        // Mon 2026-12-28 to Tue 2027-01-05, no holidays
        var days = LeaveRules.workingDays(d(2026, 12, 28), d(2027, 1, 5), Set.of());
        var byYear = LeaveRules.daysByYear(days, false);
        assertThat(byYear.get(2026)).isEqualByComparingTo("4");
        assertThat(byYear.get(2027)).isEqualByComparingTo("3");
    }

    @Test
    void halfDayCountsAsHalf() {
        var days = LeaveRules.workingDays(d(2026, 10, 22), d(2026, 10, 22), Set.of());
        assertThat(LeaveRules.totalDays(days, true)).isEqualByComparingTo("0.5");
        assertThat(LeaveRules.daysByYear(days, true).get(2026)).isEqualByComparingTo("0.5");
    }

    @Test
    void annualLeaveEarnedIsOneDayPer17WeekdaysAndStopsAtFifteen() {
        // 2026 weekdays from 1 Jan to 31 Mar: 22 + 20 + 22 = 64 -> 64/17 = 3.76
        assertThat(LeaveRules.annualEarned(STARTED, d(2026, 1, 1), 2026, d(2026, 3, 31))).isEqualByComparingTo("3.76");
        assertThat(LeaveRules.annualEarned(STARTED, d(2026, 1, 1), 2026, d(2026, 12, 31))).isEqualByComparingTo("15");
    }

    @Test
    void annualLeaveStartsFromTheStartDateOrTheLedgerStart() {
        // started 1 Oct 2026: weekdays 1 Oct to 21 Oct = 15 -> 0.88
        assertThat(LeaveRules.annualEarned(d(2026, 10, 1), d(2026, 1, 1), 2026, TODAY)).isEqualByComparingTo("0.88");
        // ledger started in 2026: nothing is earned for 2025
        assertThat(LeaveRules.annualEarned(STARTED, d(2026, 1, 1), 2025, d(2025, 12, 31))).isEqualByComparingTo("0");
    }

    @Test
    void annualLeaveForAFutureYearIsNotEarnedYet() {
        assertThat(LeaveRules.annualEarned(STARTED, d(2026, 1, 1), 2027, TODAY)).isEqualByComparingTo("0");
    }

    @Test
    void familyResponsibilityNeedsFourMonthsOfService() {
        assertThat(LeaveRules.familyEarned(d(2026, 8, 1), 2026, TODAY)).isEqualByComparingTo("0");
        assertThat(LeaveRules.familyEarned(d(2026, 6, 1), 2026, TODAY)).isEqualByComparingTo("3");
        assertThat(LeaveRules.familyEarned(STARTED, 2026, TODAY)).isEqualByComparingTo("3");
    }

    @Test
    void sickCycleIs36MonthsFromTheStartDate() {
        LeaveRules.Cycle c = LeaveRules.sickCycle(STARTED, TODAY);
        assertThat(c.from()).isEqualTo(d(2024, 3, 1));
        assertThat(c.to()).isEqualTo(d(2027, 2, 28));
        LeaveRules.Cycle next = LeaveRules.sickCycle(STARTED, d(2027, 3, 1));
        assertThat(next.from()).isEqualTo(d(2027, 3, 1));
    }

    @Test
    void sickLeaveInTheFirstSixMonthsIsOneDayPer26DaysWorked() {
        // 1 Oct 2026 start; to 21 Oct weekdays = 15 -> 0
        assertThat(LeaveRules.sickEarned(d(2026, 10, 1), TODAY)).isEqualByComparingTo("0");
        // 1 Jun 2026 start; to 21 Oct weekdays: 22 + 23 + 21 + 22 + 15 = 103 -> 3
        assertThat(LeaveRules.sickEarned(d(2026, 6, 1), TODAY)).isEqualByComparingTo("3");
    }

    @Test
    void sickLeaveIsThirtyAfterSixMonthsAndAtTheStartOfALaterCycle() {
        assertThat(LeaveRules.sickEarned(STARTED, TODAY)).isEqualByComparingTo("30");
        assertThat(LeaveRules.sickEarned(STARTED, d(2027, 3, 1))).isEqualByComparingTo("30");
    }

    @Test
    void aSimpleRequestIsFine() {
        assertThat(LeaveRules.requestProblem("ANNUAL", d(2026, 11, 2), d(2026, 11, 6), false, 5, STARTED, "ACTIVE", TODAY)).isNull();
    }

    @Test
    void requestProblems() {
        assertThat(LeaveRules.requestProblem("BOGUS", d(2026, 11, 2), d(2026, 11, 6), false, 5, STARTED, "ACTIVE", TODAY)).contains("leave type");
        assertThat(LeaveRules.requestProblem("ANNUAL", d(2026, 11, 6), d(2026, 11, 2), false, 0, STARTED, "ACTIVE", TODAY)).contains("before the first");
        assertThat(LeaveRules.requestProblem("ANNUAL", d(2026, 11, 2), d(2026, 11, 3), true, 2, STARTED, "ACTIVE", TODAY)).contains("half day");
        assertThat(LeaveRules.requestProblem("ANNUAL", d(2026, 11, 2), d(2026, 11, 6), false, 5, STARTED, "TERMINATED", TODAY)).contains("active");
        assertThat(LeaveRules.requestProblem("ANNUAL", d(2026, 11, 7), d(2026, 11, 8), false, 0, STARTED, "ACTIVE", TODAY)).contains("no working day");
        assertThat(LeaveRules.requestProblem("ANNUAL", d(2023, 1, 2), d(2023, 1, 3), false, 2, STARTED, "ACTIVE", TODAY)).contains("before the employee");
    }

    @Test
    void backdatingIsLimited() {
        assertThat(LeaveRules.requestProblem("ANNUAL", d(2025, 12, 29), d(2025, 12, 30), false, 2, STARTED, "ACTIVE", TODAY)).contains("closed");
        assertThat(LeaveRules.requestProblem("SICK", d(2026, 6, 1), d(2026, 6, 2), false, 2, STARTED, "ACTIVE", TODAY)).contains("90 days");
        assertThat(LeaveRules.requestProblem("SICK", d(2026, 10, 15), d(2026, 10, 16), false, 2, STARTED, "ACTIVE", TODAY)).isNull();
    }

    @Test
    void sickLeaveOverTwoWorkingDaysExpectsACertificate() {
        assertThat(LeaveRules.certificateExpected("SICK", 3)).isTrue();
        assertThat(LeaveRules.certificateExpected("SICK", 2)).isFalse();
        assertThat(LeaveRules.certificateExpected("ANNUAL", 10)).isFalse();
    }

    @Test
    void overlapIsInclusiveOfBothEnds() {
        assertThat(LeaveRules.overlaps(d(2026, 11, 2), d(2026, 11, 6), d(2026, 11, 6), d(2026, 11, 9))).isTrue();
        assertThat(LeaveRules.overlaps(d(2026, 11, 2), d(2026, 11, 5), d(2026, 11, 6), d(2026, 11, 9))).isFalse();
    }

    @Test
    void capsAndTypes() {
        assertThat(LeaveRules.isCapped("ANNUAL")).isTrue();
        assertThat(LeaveRules.isCapped("UNPAID")).isFalse();
        assertThat(LeaveRules.isKnown("MATERNITY")).isTrue();
        assertThat(LeaveRules.isKnown(null)).isFalse();
    }
}
