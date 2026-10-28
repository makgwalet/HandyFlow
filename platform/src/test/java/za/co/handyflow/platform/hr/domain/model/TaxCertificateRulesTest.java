package za.co.handyflow.platform.hr.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaxCertificateRulesTest {

    private static BigDecimal d(String v) { return new BigDecimal(v); }

    private static TaxCertificateRules.Totals totals(String basic, String travel, String pension, String medical, String paye,
                                                     String uifEe, String uifEr, String sdl, String credit, int slips, int noCredit) {
        return new TaxCertificateRules.Totals(d(basic), d("0"), d("0"), d("0"), d(travel), d(pension), d(medical), d(paye),
                d(uifEe), d(uifEr), d(sdl), d(credit), slips, noCredit);
    }

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 21);

    @Test
    void theInterimPeriodIsMarchToAugustAndTheAnnualOneRunsToTheEndOfFebruary() {
        var interim = TaxCertificateRules.window(2027, TaxCertificateRules.INTERIM);
        assertThat(interim.start()).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(interim.end()).isEqualTo(LocalDate.of(2026, 8, 31));
        var annual = TaxCertificateRules.window(2027, TaxCertificateRules.ANNUAL);
        assertThat(annual.end()).isEqualTo(LocalDate.of(2027, 2, 28));
        assertThat(TaxCertificateRules.window(2028, TaxCertificateRules.ANNUAL).end()).isEqualTo(LocalDate.of(2028, 2, 29));
        assertThat(annual.contains(LocalDate.of(2026, 3, 1))).isTrue();
        assertThat(annual.contains(LocalDate.of(2026, 2, 28))).isFalse();
        assertThat(TaxCertificateRules.periodDigits("INTERIM")).isEqualTo("08");
        assertThat(TaxCertificateRules.periodDigits("ANNUAL")).isEqualTo("02");
        assertThatThrownBy(() -> TaxCertificateRules.window(2027, "MONTHLY")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theInterimWindowMatchesThePeriodSarsAnnouncedForThisYear() {
        // SARS: interim 2026 covers 1 March 2026 to 31 August 2026. Our reconciliation year is named for the year it ends in.
        var w = TaxCertificateRules.window(2027, TaxCertificateRules.INTERIM);
        assertThat(w.start()).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(w.end()).isEqualTo(LocalDate.of(2026, 8, 31));
    }

    @Test
    void aSalariedEmployeeGetsTheCodesTheirPayslipsSupport() {
        var t = totals("180000.00", "0", "13500.00", "7200.00", "21000.50", "1060.20", "1060.20", "1800.00", "6120.00", 6, 0);
        var lines = TaxCertificateRules.lines(t, TaxCertificateRules.PENSION);

        assertThat(lines).extracting(TaxCertificateRules.Line::code)
                .containsExactly("3601", "4001", "4005", "4102", "4116", "4141", "4142");
        assertThat(amount(lines, "4102")).isEqualByComparingTo("21000.50");
        assertThat(amount(lines, "4141")).isEqualByComparingTo("2120.40");
    }

    @Test
    void theFundTypeChoosesThePensionProvidentOrAnnuityCode() {
        var t = totals("1000", "0", "100", "0", "10", "1", "1", "0", "0", 1, 0);
        assertThat(codes(TaxCertificateRules.lines(t, TaxCertificateRules.PROVIDENT))).contains("4003").doesNotContain("4001", "4006");
        assertThat(codes(TaxCertificateRules.lines(t, TaxCertificateRules.RETIREMENT_ANNUITY))).contains("4006").doesNotContain("4001", "4003");
        assertThat(codes(TaxCertificateRules.lines(t, null))).contains("4001");
    }

    @Test
    void zeroAmountsAreLeftOutExceptTheTaxCodes() {
        var t = totals("50000", "0", "0", "0", "0", "0", "0", "0", "0", 3, 0);
        var lines = TaxCertificateRules.lines(t, TaxCertificateRules.PENSION);

        assertThat(codes(lines)).containsExactly("3601", "4102", "4141", "4142");
        assertThat(amount(lines, "4102")).isEqualByComparingTo("0.00");
    }

    @Test
    void anAllowanceAndOtherIncomeUseTheirOwnCodes() {
        var t = new TaxCertificateRules.Totals(d("1000"), d("200"), d("300"), d("50"), d("400"), d("0"), d("0"), d("10"), d("1"), d("1"), d("0"), d("0"), 1, 1);
        assertThat(codes(TaxCertificateRules.lines(t, "PENSION"))).containsExactly("3601", "3605", "3607", "3701", "3713", "4102", "4141", "4142");
    }

    @Test
    void amountsAreRoundedToCents() {
        var t = totals("100.005", "0", "0", "0", "1.004", "0", "0", "0", "0", 1, 0);
        var lines = TaxCertificateRules.lines(t, "PENSION");
        assertThat(amount(lines, "3601")).isEqualByComparingTo("100.01");
        assertThat(amount(lines, "4102")).isEqualByComparingTo("1.00");
    }

    @Test
    void itIsAnIrp5WhenTaxWasDeductedAndAnIt3aWhenNoneWas() {
        assertThat(TaxCertificateRules.certificateType(totals("1", "0", "0", "0", "5.00", "0", "0", "0", "0", 1, 0))).isEqualTo("IRP5");
        assertThat(TaxCertificateRules.certificateType(totals("1", "0", "0", "0", "0.00", "0", "0", "0", "0", 1, 0))).isEqualTo("IT3A");
    }

    @Test
    void theCertificateNumberIsThirtyDigitsInSarsOrder() {
        String n = TaxCertificateRules.certificateNumber("7123456789", 2027, "INTERIM", 42);
        assertThat(n).hasSize(30).isEqualTo("7123456789" + "2027" + "08" + "00000000000042");
        assertThat(TaxCertificateRules.certificateNumber("7123456789", 2027, "ANNUAL", 1)).startsWith("71234567892027" + "02");
        assertThatThrownBy(() -> TaxCertificateRules.certificateNumber("123", 2027, "ANNUAL", 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TaxCertificateRules.certificateNumber(null, 2027, "ANNUAL", 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TaxCertificateRules.certificateNumber("7123456789", 2027, "ANNUAL", -1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theEmployerNeedsAPayeReference() {
        assertThat(TaxCertificateRules.employerProblems(null, "L1", "U1")).anyMatch(p -> p.isError() && p.message().contains("PAYE reference"));
        assertThat(TaxCertificateRules.employerProblems("12345", "L1", "U1")).anyMatch(TaxCertificateRules.Problem::isError);
        var ok = TaxCertificateRules.employerProblems("7123456789", null, "");
        assertThat(ok).noneMatch(TaxCertificateRules.Problem::isError);
        assertThat(ok).hasSize(2);
    }

    @Test
    void anEmployeeWithoutAnIdNumberCannotGetACertificate() {
        var t = totals("1000", "0", "0", "0", "100", "10", "10", "0", "0", 1, 0);
        var probs = TaxCertificateRules.employeeProblems("Jane", "Jane", "Dlamini", null, null, TODAY, t, TaxCertificateRules.lines(t, "PENSION"));
        assertThat(probs).anyMatch(p -> p.isError() && p.message().contains("ID number"));
    }

    @Test
    void aWrongIdOrTaxNumberIsAnError() {
        var t = totals("1000", "0", "0", "0", "100", "10", "10", "0", "0", 1, 0);
        var lines = TaxCertificateRules.lines(t, "PENSION");
        assertThat(TaxCertificateRules.employeeProblems("J", "Jane", "D", "1234", "0123456789", TODAY, t, lines)).anyMatch(TaxCertificateRules.Problem::isError);
        assertThat(TaxCertificateRules.employeeProblems("J", "Jane", "D", "8001015009087", "123", TODAY, t, lines))
                .anyMatch(p -> p.isError() && p.message().contains("10 digits"));
    }

    @Test
    void aCompleteEmployeeHasNoErrors() {
        var t = totals("1000", "0", "0", "0", "100", "10", "10", "0", "0", 1, 0);
        var probs = TaxCertificateRules.employeeProblems("J", "Jane", "D", "8001015009087", "0123456789", TODAY, t, TaxCertificateRules.lines(t, "PENSION"));
        assertThat(probs).isEmpty();
    }

    @Test
    void missingNamesAndNoIncomeAreErrors() {
        var t = totals("0", "0", "0", "0", "0", "0", "0", "0", "0", 0, 0);
        var probs = TaxCertificateRules.employeeProblems("J", " ", null, "8001015009087", "0123456789", TODAY, t, TaxCertificateRules.lines(t, "PENSION"));
        assertThat(probs).anyMatch(p -> p.message().contains("surname"));
        assertThat(probs).anyMatch(p -> p.message().contains("income code"));
    }

    @Test
    void taxMoreThanIncomeIsAnError() {
        var t = totals("100", "0", "0", "0", "500", "0", "0", "0", "0", 1, 0);
        assertThat(TaxCertificateRules.employeeProblems("J", "Jane", "D", "8001015009087", "0123456789", TODAY, t, TaxCertificateRules.lines(t, "PENSION")))
                .anyMatch(p -> p.isError() && p.message().contains("more than the income"));
    }

    @Test
    void payslipsWithoutARecordedCreditWarn() {
        var t = totals("1000", "0", "0", "0", "100", "10", "10", "0", "0", 4, 2);
        var probs = TaxCertificateRules.employeeProblems("J", "Jane", "D", "8001015009087", "0123456789", TODAY, t, TaxCertificateRules.lines(t, "PENSION"));
        assertThat(probs).anyMatch(p -> !p.isError() && p.message().contains("4116"));
    }

    @Test
    void reconciliationBalancesOnlyWhenAllThreeAgree() {
        var ok = TaxCertificateRules.reconLine("PAYE", d("100.00"), d("100.00"), d("100.00"));
        assertThat(ok.balanced()).isTrue();
        var off = TaxCertificateRules.reconLine("PAYE", d("100.00"), d("100.00"), d("90.50"));
        assertThat(off.balanced()).isFalse();
        assertThat(off.difference()).isEqualByComparingTo("9.50");
    }

    @Test
    void settingsAreChecked() {
        assertThat(TaxCertificateRules.settingsProblem("7123456789", "PENSION")).isNull();
        assertThat(TaxCertificateRules.settingsProblem(null, "PROVIDENT")).isNull();
        assertThat(TaxCertificateRules.settingsProblem("12", "PENSION")).contains("10 digits");
        assertThat(TaxCertificateRules.settingsProblem("7123456789", "OTHER")).contains("pension");
    }

    private static List<String> codes(List<TaxCertificateRules.Line> l) { return l.stream().map(TaxCertificateRules.Line::code).toList(); }
    private static BigDecimal amount(List<TaxCertificateRules.Line> l, String code) {
        return l.stream().filter(x -> x.code().equals(code)).findFirst().orElseThrow().amount();
    }

    @Test
    void aPassportHolderCanGetACertificateWithoutAnIdNumber() {
        var t = totals("1000", "0", "0", "0", "100", "10", "10", "0", "0", 1, 0);
        var lines = TaxCertificateRules.lines(t, "PENSION");
        var probs = TaxCertificateRules.employeeProblems("Tendai", "Tendai", "Moyo", null, "AB123456", "ZW", "0123456789", TODAY, t, lines);
        assertThat(probs).noneMatch(TaxCertificateRules.Problem::isError);
    }

    @Test
    void aPassportWithoutItsCountryOrAnIdIsStillAnError() {
        var t = totals("1000", "0", "0", "0", "100", "10", "10", "0", "0", 1, 0);
        var lines = TaxCertificateRules.lines(t, "PENSION");
        assertThat(TaxCertificateRules.employeeProblems("T", "Tendai", "Moyo", null, "AB123456", null, "0123456789", TODAY, t, lines))
                .anyMatch(p -> p.isError() && p.message().contains("ID number or passport"));
        assertThat(TaxCertificateRules.employeeProblems("T", "Tendai", "Moyo", null, null, null, "0123456789", TODAY, t, lines))
                .anyMatch(p -> p.isError() && p.message().contains("ID number or passport"));
    }

    @Test
    void aBadIdIsOnlyAWarningWhenAPassportCanStandInForIt() {
        var t = totals("1000", "0", "0", "0", "100", "10", "10", "0", "0", 1, 0);
        var lines = TaxCertificateRules.lines(t, "PENSION");
        var probs = TaxCertificateRules.employeeProblems("T", "Tendai", "Moyo", "1234", "AB123456", "ZW", "0123456789", TODAY, t, lines);
        assertThat(probs).noneMatch(TaxCertificateRules.Problem::isError);
        assertThat(probs).anyMatch(p -> !p.isError() && p.message().contains("passport is used"));
    }

    @Test
    void severanceIsReportedUnder3901WithTheDirectedTaxUnder4115() {
        var t = new TaxCertificateRules.Totals(d("30000"), d("0"), d("0"), d("0"), d("0"), d("0"), d("0"), d("2000"), d("300"), d("300"), d("300"), d("0"), 1, 0,
                d("0"), d("0"), d("0"), d("60000"), d("4500.50"));
        var lines = TaxCertificateRules.lines(t, null);
        assertThat(lines.stream().anyMatch(l -> l.code().equals("3901") && l.amount().compareTo(d("60000")) == 0)).isTrue();
        assertThat(lines.stream().anyMatch(l -> l.code().equals("4115") && l.amount().compareTo(d("4500.50")) == 0)).isTrue();
        assertThat(lines.stream().anyMatch(l -> l.code().equals("4102") && l.amount().compareTo(d("2000")) == 0)).isTrue();
        assertThat(TaxCertificateRules.certificateType(t)).isEqualTo("IRP5");
        var none = new TaxCertificateRules.Totals(d("30000"), d("0"), d("0"), d("0"), d("0"), d("0"), d("0"), d("0"), d("0"), d("0"), d("0"), d("0"), 1, 0);
        assertThat(TaxCertificateRules.lines(none, null).stream().noneMatch(l -> l.code().equals("3901") || l.code().equals("4115"))).isTrue();
    }

    @Test
    void aCertificateWithOnlyDirectedTaxIsAnIrp5() {
        var t = new TaxCertificateRules.Totals(d("0"), d("0"), d("0"), d("0"), d("0"), d("0"), d("0"), d("0"), d("0"), d("0"), d("0"), d("0"), 1, 0,
                d("0"), d("0"), d("0"), d("60000"), d("4500"));
        assertThat(TaxCertificateRules.certificateType(t)).isEqualTo("IRP5");
    }
}
