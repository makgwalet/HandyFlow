package za.co.handyflow.platform.payrollbureau.domain.rules;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class PayPeriodRulesTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 3, 2);

    @Test
    void monthlyPeriodsAreCalendarMonths() {
        var p = PayPeriodRules.periodContaining("MONTHLY", null, LocalDate.of(2027, 2, 14));
        assertEquals(LocalDate.of(2027, 2, 1), p.start());
        assertEquals(LocalDate.of(2027, 2, 28), p.end());
        assertEquals(LocalDate.of(2028, 2, 29), PayPeriodRules.periodContaining("MONTHLY", null, LocalDate.of(2028, 2, 10)).end());
    }

    @Test
    void weeklyPeriodsCountSevenDaysFromTheAnchor() {
        var p = PayPeriodRules.periodContaining("WEEKLY", MONDAY, LocalDate.of(2026, 3, 11));
        assertEquals(LocalDate.of(2026, 3, 9), p.start());
        assertEquals(LocalDate.of(2026, 3, 15), p.end());
    }

    @Test
    void fortnightlyPeriodsCountFourteenDaysFromTheAnchor() {
        var p = PayPeriodRules.periodContaining("FORTNIGHTLY", MONDAY, LocalDate.of(2026, 3, 20));
        assertEquals(LocalDate.of(2026, 3, 16), p.start());
        assertEquals(LocalDate.of(2026, 3, 29), p.end());
    }

    @Test
    void aDateBeforeTheAnchorStillFallsInAPeriod() {
        var p = PayPeriodRules.periodContaining("WEEKLY", MONDAY, LocalDate.of(2026, 2, 27));
        assertEquals(LocalDate.of(2026, 2, 23), p.start());
        assertEquals(LocalDate.of(2026, 3, 1), p.end());
    }

    @Test
    void theNextPeriodStartsTheDayAfterThePreviousEnds() {
        var p = PayPeriodRules.periodAfter("WEEKLY", MONDAY, LocalDate.of(2026, 3, 15));
        assertEquals(LocalDate.of(2026, 3, 16), p.start());
        var m = PayPeriodRules.periodAfter("MONTHLY", null, LocalDate.of(2026, 12, 31));
        assertEquals(LocalDate.of(2027, 1, 1), m.start());
    }

    @Test
    void aRunMustCoverExactlyOnePeriod() {
        assertNull(PayPeriodRules.periodProblem("WEEKLY", MONDAY, LocalDate.of(2026, 3, 9), LocalDate.of(2026, 3, 15)));
        assertNotNull(PayPeriodRules.periodProblem("WEEKLY", MONDAY, LocalDate.of(2026, 3, 9), LocalDate.of(2026, 3, 22)));
        assertNotNull(PayPeriodRules.periodProblem("WEEKLY", MONDAY, LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 16)));
        assertNull(PayPeriodRules.periodProblem("MONTHLY", null, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31)));
    }

    @Test
    void groupNeedsANameAKnownFrequencyAndAnAnchorWhenNotMonthly() {
        assertNull(PayPeriodRules.groupProblem("Factory", "WEEKLY", MONDAY, 2));
        assertNotNull(PayPeriodRules.groupProblem(" ", "WEEKLY", MONDAY, 2));
        assertNotNull(PayPeriodRules.groupProblem("Factory", "DAILY", MONDAY, 2));
        assertNotNull(PayPeriodRules.groupProblem("Factory", "WEEKLY", null, 2));
        assertNull(PayPeriodRules.groupProblem("Office", "MONTHLY", null, 0));
        assertNotNull(PayPeriodRules.groupProblem("Office", "MONTHLY", null, 40));
    }

    @Test
    void payDateIsTheLagAfterPeriodEndAndTaxYearFollowsThePayDate() {
        var p = PayPeriodRules.periodContaining("WEEKLY", MONDAY, LocalDate.of(2027, 2, 24));
        LocalDate pay = PayPeriodRules.payDateFor(p, 3);
        assertEquals(p.end().plusDays(3), pay);
        // a week ending 28 Feb 2027 and paid on 3 Mar 2027 falls in the next tax year
        assertEquals(2027, PayPeriodRules.taxYearOfPayDate(LocalDate.of(2027, 2, 28)));
        assertEquals(2028, PayPeriodRules.taxYearOfPayDate(LocalDate.of(2027, 3, 3)));
    }

    @Test
    void periodsPerYearMatchTheSarsMultipliers() {
        assertEquals(12, PayPeriodRules.periodsPerYear("MONTHLY"));
        assertEquals(26, PayPeriodRules.periodsPerYear("FORTNIGHTLY"));
        assertEquals(52, PayPeriodRules.periodsPerYear("WEEKLY"));
        assertThrows(IllegalArgumentException.class, () -> PayPeriodRules.periodsPerYear("DAILY"));
    }
}
