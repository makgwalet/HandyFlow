package za.co.handyflow.platform.hr.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class PayCorrectionRulesTest {

    private static BigDecimal n(String v) { return new BigDecimal(v); }

    @Test
    void underpaidIsBackPayAndOverpaidIsARecovery() {
        assertThat(PayCorrectionRules.kindFor("UNDERPAID")).isEqualTo("BACK_PAY");
        assertThat(PayCorrectionRules.kindFor("OVERPAID")).isEqualTo("RECOVERY");
        assertThat(PayCorrectionRules.kindFor("OTHER")).isNull();
        assertThat(PayCorrectionRules.kindFor(null)).isNull();
    }

    @Test
    void onlyAnIssuedRunsPayslipCanBeCorrected() {
        assertThat(PayCorrectionRules.problem("UNDERPAID", n("100"), n("1000"), "FINALISED", "ACTIVE", false)).isNull();
        assertThat(PayCorrectionRules.problem("UNDERPAID", n("100"), n("1000"), "COMPLETED", "ACTIVE", false)).isNull();
        assertThat(PayCorrectionRules.problem("UNDERPAID", n("100"), n("1000"), "DRAFT", "ACTIVE", false)).contains("finalised pay run");
        assertThat(PayCorrectionRules.problem("UNDERPAID", n("100"), n("1000"), "APPROVED", "ACTIVE", false)).contains("finalised pay run");
        assertThat(PayCorrectionRules.problem("UNDERPAID", n("100"), n("1000"), "CANCELLED", "ACTIVE", false)).contains("finalised pay run");
    }

    @Test
    void aLeaverIsSettledOutsidePayroll() {
        assertThat(PayCorrectionRules.problem("OVERPAID", n("100"), n("1000"), "FINALISED", "TERMINATED", false)).contains("has left");
    }

    @Test
    void anOverpaymentCannotBeMoreThanWhatWasPaid() {
        assertThat(PayCorrectionRules.problem("OVERPAID", n("1000"), n("1000"), "FINALISED", "ACTIVE", false)).isNull();
        assertThat(PayCorrectionRules.problem("OVERPAID", n("1000.01"), n("1000"), "FINALISED", "ACTIVE", false)).contains("cannot be more than the net pay");
        assertThat(PayCorrectionRules.problem("UNDERPAID", n("5000"), n("1000"), "FINALISED", "ACTIVE", false)).isNull();
    }

    @Test
    void needsADirectionAnAmountAndNoTwinWaiting() {
        assertThat(PayCorrectionRules.problem(null, n("1"), n("1000"), "FINALISED", "ACTIVE", false)).contains("underpaid or overpaid");
        assertThat(PayCorrectionRules.problem("UNDERPAID", null, n("1000"), "FINALISED", "ACTIVE", false)).contains("Enter the amount");
        assertThat(PayCorrectionRules.problem("UNDERPAID", BigDecimal.ZERO, n("1000"), "FINALISED", "ACTIVE", false)).contains("Enter the amount");
        assertThat(PayCorrectionRules.problem("UNDERPAID", n("1"), n("1000"), "FINALISED", "ACTIVE", true)).contains("already waiting");
    }

    @Test
    void theBackPayPeriodStaysInsideTheEmployeesServiceAndTodayAndTheDescriptionNamesTheRun() {
        LocalDate start = LocalDate.of(2026, 10, 1), end = LocalDate.of(2026, 10, 31);
        assertThat(PayCorrectionRules.relatesFrom(start, LocalDate.of(2026, 10, 15))).isEqualTo(LocalDate.of(2026, 10, 15));
        assertThat(PayCorrectionRules.relatesFrom(start, LocalDate.of(2024, 1, 1))).isEqualTo(start);
        assertThat(PayCorrectionRules.relatesTo(end, LocalDate.of(2026, 10, 28))).isEqualTo(LocalDate.of(2026, 10, 28));
        assertThat(PayCorrectionRules.relatesTo(end, LocalDate.of(2026, 11, 5))).isEqualTo(end);
        assertThat(PayCorrectionRules.description(" PR00005 ")).isEqualTo("Correction of payslip PR00005");
    }
}
