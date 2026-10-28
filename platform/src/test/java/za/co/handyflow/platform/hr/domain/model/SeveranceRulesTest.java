package za.co.handyflow.platform.hr.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class SeveranceRulesTest {

    private static BigDecimal n(String v) { return new BigDecimal(v); }
    private static LocalDate d(int y, int m, int day) { return LocalDate.of(y, m, day); }
    private static final LocalDate START = d(2019, 3, 1), LAST = d(2026, 10, 31);   // 7 completed years
    private static final BigDecimal MIN = n("55384.62");

    @Test
    void theMinimumIsAWeeksPayForEachCompletedYear() {
        assertThat(SeveranceRules.completedYears(START, LAST)).isEqualTo(7);
        assertThat(SeveranceRules.completedYears(START, d(2020, 2, 28))).isEqualTo(0);
        assertThat(SeveranceRules.completedYears(START, d(2020, 3, 1))).isEqualTo(1);
        assertThat(SeveranceRules.weeklyPay(n("30000"))).isEqualTo(n("6923.08"));
        assertThat(SeveranceRules.statutoryMinimum(n("30000"), START, LAST)).isEqualTo(n("48461.56"));
        assertThat(SeveranceRules.statutoryMinimum(n("30000"), START, d(2020, 1, 1))).isEqualTo(n("0.00"));
        assertThat(SeveranceRules.statutoryMinimum(null, START, LAST)).isEqualTo(n("0.00"));
    }

    @Test
    void aDirectiveNumberIsNeeded() {
        assertThat(SeveranceRules.directiveProblem(null)).contains("directive number");
        assertThat(SeveranceRules.directiveProblem("  ")).contains("directive number");
        assertThat(SeveranceRules.directiveProblem("x".repeat(41))).contains("too long");
        assertThat(SeveranceRules.directiveProblem("12$34")).contains("digits");
        assertThat(SeveranceRules.directiveProblem("IRP3-2026/001234")).isNull();
    }

    @Test
    void severanceIsEnteredOnlyForARetrenchmentThatHasEnded() {
        assertThat(SeveranceRules.problem("TERMINATED", "RETRENCHMENT", n("60000"), "D1", n("5000"), false, MIN, false)).isNull();
        assertThat(SeveranceRules.problem("ACTIVE", "RETRENCHMENT", n("60000"), "D1", n("5000"), false, MIN, false)).contains("once employment has ended");
        assertThat(SeveranceRules.problem("TERMINATED", null, n("60000"), "D1", n("5000"), false, MIN, false)).contains("how employment ended");
        assertThat(SeveranceRules.problem("TERMINATED", "EMPLOYER_NOTICE", n("60000"), "D1", n("5000"), false, MIN, false)).contains("not a retrenchment");
        assertThat(SeveranceRules.problem("TERMINATED", "RETRENCHMENT", n("60000"), "D1", n("5000"), false, MIN, true)).contains("already entered");
    }

    @Test
    void theAmountAndTheDirectedTaxAreChecked() {
        assertThat(SeveranceRules.problem("TERMINATED", "RETRENCHMENT", null, "D1", n("0"), false, MIN, false)).contains("severance amount");
        assertThat(SeveranceRules.problem("TERMINATED", "RETRENCHMENT", n("0"), "D1", n("0"), false, MIN, false)).contains("severance amount");
        assertThat(SeveranceRules.problem("TERMINATED", "RETRENCHMENT", n("60000.123"), "D1", n("0"), false, MIN, false)).contains("two decimals");
        assertThat(SeveranceRules.problem("TERMINATED", "RETRENCHMENT", n("60000"), "", n("0"), false, MIN, false)).contains("directive number");
        assertThat(SeveranceRules.problem("TERMINATED", "RETRENCHMENT", n("60000"), "D1", null, false, MIN, false)).contains("tax SARS directed");
        assertThat(SeveranceRules.problem("TERMINATED", "RETRENCHMENT", n("60000"), "D1", n("-1"), false, MIN, false)).contains("tax SARS directed");
        assertThat(SeveranceRules.problem("TERMINATED", "RETRENCHMENT", n("60000"), "D1", n("60000.01"), false, MIN, false)).contains("more than the severance");
        assertThat(SeveranceRules.problem("TERMINATED", "RETRENCHMENT", n("60000"), "D1", n("60000"), false, MIN, false)).isNull();
        assertThat(SeveranceRules.problem("TERMINATED", "RETRENCHMENT", n("60000"), "D1", n("0.001"), false, MIN, false)).contains("two decimals");
    }

    @Test
    void lessThanTheMinimumNeedsARefusalOfOtherWork() {
        assertThat(SeveranceRules.problem("TERMINATED", "RETRENCHMENT", n("55384.61"), "D1", n("0"), false, MIN, false)).contains("BCEA minimum of 55384.62");
        assertThat(SeveranceRules.problem("TERMINATED", "RETRENCHMENT", n("55384.62"), "D1", n("0"), false, MIN, false)).isNull();
        assertThat(SeveranceRules.problem("TERMINATED", "RETRENCHMENT", n("100"), "D1", n("0"), true, MIN, false)).isNull();
    }

    @Test
    void theEmployeeReceivesTheSeveranceLessTheDirectedTax() {
        assertThat(SeveranceRules.net(n("60000"), n("4500.50"))).isEqualTo(n("55499.50"));
        assertThat(SeveranceRules.net(n("60000"), null)).isEqualTo(n("60000"));
        assertThat(SeveranceRules.net(null, null)).isEqualTo(BigDecimal.ZERO);
    }

    @Test
    void theSummaryIsOnlyForARetrenchment() {
        assertThat(SeveranceRules.summary("EMPLOYER_NOTICE", n("30000"), START, LAST)).isNull();
        assertThat(SeveranceRules.summary("RETRENCHMENT", n("30000"), START, LAST)).contains("48461.56").contains("7 completed years").contains("directive");
        assertThat(SeveranceRules.summary("RETRENCHMENT", n("30000"), d(2026, 1, 1), LAST)).contains("nil");
        assertThat(SeveranceRules.summary("RETRENCHMENT", n("30000"), d(2025, 10, 1), LAST)).contains("1 completed year)");
    }

    @Test
    void aRetrenchmentIsEmployerInitiatedSoNoticeApplies() {
        assertThat(NoticeRules.TYPES).contains("RETRENCHMENT");
        assertThat(NoticeRules.employerEnds("RETRENCHMENT")).isTrue();
        assertThat(NoticeRules.employerEnds("EMPLOYER_NOTICE")).isTrue();
        assertThat(NoticeRules.employerEnds("RESIGNATION")).isFalse();
        assertThat(NoticeRules.shortfallDays("RETRENCHMENT", START, null, LAST)).isEqualTo(28);
        assertThat(NoticePayRules.problem("TERMINATED", "RETRENCHMENT", n("100"), n("100"), n("200"), false)).isNull();
        assertThat(NoticeRules.problem("RETRENCHMENT", null, LAST, START)).isNull();
    }
}
