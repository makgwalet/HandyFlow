package za.co.handyflow.platform.hr.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TaxCertificateBenefitCodesTest {

    private static BigDecimal d(String s) { return new BigDecimal(s); }

    private static TaxCertificateRules.Totals totals(String medical, String retirement, String fringe) {
        return new TaxCertificateRules.Totals(d("360000"), d("0"), d("0"), d("0"), d("0"), d("24000"), d("0"), d("60000"), d("2000"), d("2000"), d("3000"), d("0"), 12, 0,
                d(medical), d(retirement), d(fringe));
    }

    private static String amount(List<TaxCertificateRules.Line> lines, String code) {
        return lines.stream().filter(l -> l.code().equals(code)).map(l -> l.amount().toPlainString()).findFirst().orElse(null);
    }

    @Test
    void anEmployerMedicalContributionIsReportedTwice() {
        List<TaxCertificateRules.Line> lines = TaxCertificateRules.lines(totals("36000", "0", "0"), TaxCertificateRules.PENSION);

        assertThat(amount(lines, "3810")).isEqualTo("36000.00");
        assertThat(amount(lines, "4474")).isEqualTo("36000.00");
    }

    @Test
    void theEmployerRetirementCodesFollowTheFundType() {
        assertThat(amount(TaxCertificateRules.lines(totals("0", "12000", "0"), TaxCertificateRules.PENSION), "3817")).isEqualTo("12000.00");
        assertThat(amount(TaxCertificateRules.lines(totals("0", "12000", "0"), TaxCertificateRules.PENSION), "4472")).isEqualTo("12000.00");
        assertThat(amount(TaxCertificateRules.lines(totals("0", "12000", "0"), TaxCertificateRules.PROVIDENT), "3825")).isEqualTo("12000.00");
        assertThat(amount(TaxCertificateRules.lines(totals("0", "12000", "0"), TaxCertificateRules.PROVIDENT), "4473")).isEqualTo("12000.00");
        assertThat(amount(TaxCertificateRules.lines(totals("0", "12000", "0"), TaxCertificateRules.RETIREMENT_ANNUITY), "3828")).isEqualTo("12000.00");
        assertThat(amount(TaxCertificateRules.lines(totals("0", "12000", "0"), TaxCertificateRules.RETIREMENT_ANNUITY), "4475")).isEqualTo("12000.00");
        assertThat(amount(TaxCertificateRules.lines(totals("0", "12000", "0"), TaxCertificateRules.PROVIDENT), "3817")).isNull();
    }

    @Test
    void otherFringeBenefitsAreGeneralFringeBenefits() {
        assertThat(amount(TaxCertificateRules.lines(totals("0", "0", "8400"), null), "3801")).isEqualTo("8400.00");
    }

    @Test
    void noBenefitsMeansNoBenefitCodes() {
        List<TaxCertificateRules.Line> lines = TaxCertificateRules.lines(totals("0", "0", "0"), null);

        assertThat(lines.stream().noneMatch(l -> List.of("3801", "3810", "3817", "3825", "3828", "4472", "4473", "4474", "4475").contains(l.code()))).isTrue();
    }

    @Test
    void theOldTotalsConstructorStillWorks() {
        TaxCertificateRules.Totals t = new TaxCertificateRules.Totals(d("1000"), d("0"), d("0"), d("0"), d("0"), d("0"), d("0"), d("10"), d("1"), d("1"), d("0"), d("0"), 1, 0);

        assertThat(t.employerMedical().signum()).isZero();
        assertThat(TaxCertificateRules.Totals.empty().otherFringe().signum()).isZero();
    }
}
