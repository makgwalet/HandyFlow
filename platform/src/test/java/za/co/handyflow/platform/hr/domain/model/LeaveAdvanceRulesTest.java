package za.co.handyflow.platform.hr.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class LeaveAdvanceRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 28);
    private static BigDecimal n(String v) { return new BigDecimal(v); }

    @Test
    void aRecoveryNeedsAWrittenAgreementNamedAndDated() {
        assertThat(LeaveAdvanceRules.problem("TERMINATED", n("5"), false, "Leave advance form 12", TODAY.minusDays(60), TODAY)).isNull();
        assertThat(LeaveAdvanceRules.problem("TERMINATED", n("5"), false, null, TODAY, TODAY)).contains("written agreement");
        assertThat(LeaveAdvanceRules.problem("TERMINATED", n("5"), false, "   ", TODAY, TODAY)).contains("written agreement");
        assertThat(LeaveAdvanceRules.problem("TERMINATED", n("5"), false, "x".repeat(101), TODAY, TODAY)).contains("too long");
        assertThat(LeaveAdvanceRules.problem("TERMINATED", n("5"), false, "Form 12", null, TODAY)).contains("date of the written agreement");
        assertThat(LeaveAdvanceRules.problem("TERMINATED", n("5"), false, "Form 12", TODAY.plusDays(1), TODAY)).contains("future");
    }

    @Test
    void onlyForSomeoneWhoHasLeftWithLeaveTakenInAdvanceAndNoRecoveryYet() {
        assertThat(LeaveAdvanceRules.problem("ACTIVE", n("5"), false, "Form 12", TODAY, TODAY)).contains("still employed");
        assertThat(LeaveAdvanceRules.problem("TERMINATED", n("0"), false, "Form 12", TODAY, TODAY)).contains("No annual leave");
        assertThat(LeaveAdvanceRules.problem("TERMINATED", null, false, "Form 12", TODAY, TODAY)).contains("No annual leave");
        assertThat(LeaveAdvanceRules.problem("TERMINATED", n("5"), true, "Form 12", TODAY, TODAY)).contains("already entered");
    }

    @Test
    void theAmountIsTheDaysAtTheDailyRateAndTheReasonCarriesTheAgreement() {
        assertThat(LeaveAdvanceRules.amount(n("26000"), n("0")).signum()).isEqualTo(0);
        assertThat(LeaveAdvanceRules.amount(n("26000"), n("2")).compareTo(PayPeriodRules.leavePayout(n("26000"), n("2")))).isEqualTo(0);
        String r = LeaveAdvanceRules.reason(n("5.50"), " Form 12 ", LocalDate.of(2026, 8, 1));
        assertThat(r).contains("5.5 day(s)").contains("Form 12, dated 2026-08-01");
        assertThat(r.length()).isLessThanOrEqualTo(300);
    }
}
