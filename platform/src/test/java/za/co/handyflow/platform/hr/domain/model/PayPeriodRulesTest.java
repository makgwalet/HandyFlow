package za.co.handyflow.platform.hr.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PayPeriodRulesTest {

    private static LocalDate d(int y, int m, int day) { return LocalDate.of(y, m, day); }
    private static BigDecimal n(String s) { return new BigDecimal(s); }
    private static final LocalDate START = d(2026, 10, 1), END = d(2026, 10, 31);   // 22 working days

    private static boolean eq(BigDecimal a, String b) { return a.compareTo(n(b)) == 0; }

    @Test
    void aFullMonthIsPaidInFull() {
        PayPeriodRules.Pay p = PayPeriodRules.pay(n("22000"), START, END, Set.of(), d(2024, 1, 1), null, BigDecimal.ZERO);
        assertThat(p.workingDays()).isEqualTo(22);
        assertThat(eq(p.paid(), "22000")).isTrue();
        assertThat(p.partPeriod()).isFalse();
    }

    @Test
    void aJoinerIsPaidForTheDaysFromTheirStartDate() {
        PayPeriodRules.Pay p = PayPeriodRules.pay(n("22000"), START, END, Set.of(), d(2026, 10, 15), null, BigDecimal.ZERO);
        assertThat(eq(p.daysPaid(), "12")).isTrue();
        assertThat(eq(p.proration(), "10000")).isTrue();
        assertThat(eq(p.paid(), "12000")).isTrue();
        assertThat(p.partPeriod()).isTrue();
    }

    @Test
    void aLeaverIsPaidUpToTheDayTheyLeft() {
        PayPeriodRules.Pay p = PayPeriodRules.pay(n("22000"), START, END, Set.of(), d(2024, 1, 1), d(2026, 10, 15), BigDecimal.ZERO);
        assertThat(eq(p.daysPaid(), "11")).isTrue();
        assertThat(eq(p.paid(), "11000")).isTrue();
    }

    @Test
    void holidaysAreNotWorkingDaysSoTheyDoNotChangeThePerDayRate() {
        PayPeriodRules.Pay p = PayPeriodRules.pay(n("21000"), START, END, Set.of(d(2026, 10, 7)), d(2026, 10, 15), null, BigDecimal.ZERO);
        // 21 working days, 12 after the 15th: 21 000 / 21 x 12
        assertThat(p.workingDays()).isEqualTo(21);
        assertThat(eq(p.paid(), "12000")).isTrue();
    }

    @Test
    void unpaidLeaveIsDeductedAndNeverMoreThanTheDaysEmployed() {
        PayPeriodRules.Pay p = PayPeriodRules.pay(n("22000"), START, END, Set.of(), d(2024, 1, 1), null, n("2.5"));
        assertThat(eq(p.unpaidAmount(), "2500")).isTrue();
        assertThat(eq(p.paid(), "19500")).isTrue();
        PayPeriodRules.Pay all = PayPeriodRules.pay(n("22000"), START, END, Set.of(), d(2026, 10, 15), null, n("30"));
        assertThat(eq(all.paid(), "0")).isTrue();
    }

    @Test
    void aPeriodWithNoWorkingDaysPaysTheSalary() {
        PayPeriodRules.Pay p = PayPeriodRules.pay(n("5000"), d(2026, 10, 3), d(2026, 10, 4), Set.of(), d(2024, 1, 1), null, BigDecimal.ZERO);
        assertThat(eq(p.paid(), "5000")).isTrue();
    }

    @Test
    void travelAllowanceFollowsTheDaysPaid() {
        PayPeriodRules.Pay p = PayPeriodRules.pay(n("22000"), START, END, Set.of(), d(2026, 10, 15), null, BigDecimal.ZERO);
        assertThat(eq(PayPeriodRules.share(n("2200"), p), "1200")).isTrue();
        PayPeriodRules.Pay full = PayPeriodRules.pay(n("22000"), START, END, Set.of(), d(2024, 1, 1), null, BigDecimal.ZERO);
        assertThat(eq(PayPeriodRules.share(n("2200"), full), "2200")).isTrue();
    }

    @Test
    void unpaidDaysCountOnlyWorkingDaysInsideThePeriod() {
        // Fri 2026-10-30 to Tue 2026-11-03: only the Friday is in October
        assertThat(eq(PayPeriodRules.unpaidDaysIn(d(2026, 10, 30), d(2026, 11, 3), false, START, END, Set.of()), "1")).isTrue();
        // Mon-Fri with Wednesday a holiday
        assertThat(eq(PayPeriodRules.unpaidDaysIn(d(2026, 10, 5), d(2026, 10, 9), false, START, END, Set.of(d(2026, 10, 7))), "4")).isTrue();
        assertThat(eq(PayPeriodRules.unpaidDaysIn(d(2026, 10, 5), d(2026, 10, 5), true, START, END, Set.of()), "0.5")).isTrue();
        assertThat(eq(PayPeriodRules.unpaidDaysIn(d(2026, 11, 2), d(2026, 11, 3), false, START, END, Set.of()), "0")).isTrue();
    }

    @Test
    void aLastPayIsOneForATerminatedEmployeeWhoHasLeft() {
        assertThat(PayPeriodRules.isFinal("TERMINATED", d(2026, 10, 15), END)).isTrue();
        assertThat(PayPeriodRules.isFinal("TERMINATED", d(2026, 11, 15), END)).isFalse();
        assertThat(PayPeriodRules.isFinal("TERMINATED", null, END)).isFalse();
        assertThat(PayPeriodRules.isFinal("ACTIVE", d(2026, 10, 15), END)).isFalse();
    }

    @Test
    void leaveIsPaidOutAtTheAnnualisedDailyRate() {
        assertThat(eq(PayPeriodRules.dailyRate(n("22000")), "1015.38")).isTrue();
        assertThat(eq(PayPeriodRules.leavePayout(n("22000"), n("10.5")), "10661.49")).isTrue();
        assertThat(eq(PayPeriodRules.leavePayout(n("22000"), BigDecimal.ZERO), "0")).isTrue();
        assertThat(eq(PayPeriodRules.leavePayout(n("22000"), n("-2")), "0")).isTrue();
    }

    @Test
    void lateLeaversCanBePaidForThreeMonthsBack() {
        assertThat(PayPeriodRules.earliestLateLeaver(START)).isEqualTo(d(2026, 7, 1));
    }
}
