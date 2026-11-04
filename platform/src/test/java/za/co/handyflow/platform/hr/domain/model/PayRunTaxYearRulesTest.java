package za.co.handyflow.platform.hr.domain.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static za.co.handyflow.platform.hr.domain.model.PayRunTaxYearRules.Verdict.*;

class PayRunTaxYearRulesTest {

    private static final LocalDate START = LocalDate.of(2026, 5, 1), END = LocalDate.of(2026, 5, 31), PAY = LocalDate.of(2026, 5, 25);

    @Test
    void mayPayDateBelongsToTheYearEnding2027() {
        var c = PayRunTaxYearRules.check(PAY, START, END, 2026);
        assertThat(c.verdict()).isEqualTo(WRONG_LABEL);
        assertThat(c.proposedYear()).isEqualTo(2027);
    }

    @Test
    void aCorrectLabelIsLeftAlone() {
        assertThat(PayRunTaxYearRules.check(PAY, START, END, 2027).verdict()).isEqualTo(CORRECT);
    }

    @Test
    void aPeriodThatEndsInAnotherTaxYearThanThePayDateNeedsReview() {
        var c = PayRunTaxYearRules.check(LocalDate.of(2026, 3, 3), LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28), 2026);
        assertThat(c.verdict()).isEqualTo(NEEDS_REVIEW);
        assertThat(c.reason()).contains("2025/26").contains("2026/27");
    }

    @Test
    void aMissingPayDateNeedsReview() {
        assertThat(PayRunTaxYearRules.check(null, START, END, 2026).verdict()).isEqualTo(NEEDS_REVIEW);
    }

    @Test
    void labelsReadAsTwoYears() {
        assertThat(PayRunTaxYearRules.label(2027)).isEqualTo("2026/27");
        assertThat(PayRunTaxYearRules.label(2026)).isEqualTo("2025/26");
    }
}
