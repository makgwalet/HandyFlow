package za.co.handyflow.platform.payrollbureau.domain.rules;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.payrollbureau.domain.rules.TaxCertificateRules.*;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TaxCertificateRulesTest {

    private static BigDecimal d(String s) { return new BigDecimal(s); }
    private static Amount a(String c, String v) { return new Amount(c, d(v)); }

    private static Input in(List<Amount> income, String tax, String paye) {
        return new Input("Ann", "8001015009087", tax, income, d("0"), d("0"), d(paye), d("178.00"), d("200.00"));
    }

    private static boolean has(Certificate c, String code) { return c.findings().stream().anyMatch(f -> f.code().equals(code)); }

    @Test
    void totalsSplitTaxableAndNonTaxableAndAddUpTheTaxLine() {
        Certificate c = TaxCertificateRules.build(in(List.of(a("3601", "20000"), a("3601", "20000"), a("3602", "500"), a("3606", "1500")), "0123456789", "5000"));
        assertEquals(d("41500.00"), c.grossTaxable3699());
        assertEquals(d("500.00"), c.nonTaxable3696());
        assertEquals(d("5378.00"), c.total4149());
        assertEquals("IRP5", c.type());
        assertEquals(3, c.income().size());
        assertEquals(d("40000.00"), c.income().get(0).amount());
        assertFalse(c.blocked());
    }

    @Test
    void noPayeMakesItAnIt3aWithAReasonNeeded() {
        Certificate c = TaxCertificateRules.build(in(List.of(a("3601", "3000")), "0123456789", "0"));
        assertEquals("IT3(a)", c.type());
        assertTrue(has(c, "REASON_4150_NEEDED"));
    }

    @Test
    void code3605IsBlockedForTheYear() {
        Certificate c = TaxCertificateRules.build(in(List.of(a("3601", "20000"), a("3605", "5000")), "0123456789", "1000"));
        assertTrue(c.blocked());
        assertTrue(has(c, "CODE_NOT_VALID"));
    }

    @Test
    void unknownCodesAreOnlyWarned() {
        Certificate c = TaxCertificateRules.build(in(List.of(a("3601", "20000"), a("3611", "100")), "0123456789", "1000"));
        assertFalse(c.blocked());
        assertTrue(has(c, "CODE_UNCHECKED"));
    }

    @Test
    void missingTaxNumberAndPayeOverIncomeBlock() {
        assertTrue(has(TaxCertificateRules.build(in(List.of(a("3601", "20000")), null, "1000")), "NO_TAX_NUMBER"));
        assertTrue(has(TaxCertificateRules.build(in(List.of(a("3601", "1000")), "0123456789", "2000")), "PAYE_EXCEEDS_INCOME"));
        assertTrue(has(TaxCertificateRules.build(in(List.of(), "0123456789", "0")), "NO_INCOME"));
    }

    @Test
    void pensionAndMedicalAreReportedWithAnUncheckedWarning() {
        Input i = new Input("Ann", "8001015009087", "0123456789", List.of(a("3601", "20000")), d("1000"), d("1500"), d("2000"), d("0"), d("0"));
        Certificate c = TaxCertificateRules.build(i);
        assertEquals(2, c.deductions().size());
        assertTrue(has(c, "DEDUCTION_CODES_UNCHECKED"));
    }

    private static Certificate cert(String paye, String uif, String sdl) {
        return TaxCertificateRules.build(new Input("X", "1", "1", List.of(a("3601", "50000")), d("0"), d("0"), d(paye), d(uif), d(sdl)));
    }

    @Test
    void reconcilesWhenEmp201AndCertificatesAgree() {
        var months = List.of(new Monthly("2026-03", "FILED", d("6000"), d("356"), d("400"), d("0"), d("0")),
                new Monthly("2026-04", "FILED", d("6000"), d("356"), d("400"), d("0"), d("0")));
        var r = TaxCertificateRules.reconcile(months, List.of("2026-03", "2026-04"),
                List.of(cert("6000", "356", "400"), cert("6000", "356", "400")));
        assertTrue(r.balanced());
        assertTrue(r.findings().isEmpty());
    }

    @Test
    void reportsDifferencesMissingAndUnfiledMonthsAndUnusedEti() {
        var months = List.of(new Monthly("2026-03", "DRAFT", d("6000"), d("356"), d("400"), d("1000"), d("250")));
        var r = TaxCertificateRules.reconcile(months, List.of("2026-03", "2026-04"), List.of(cert("5900", "356", "400")));
        assertFalse(r.balanced());
        assertEquals(d("100.00"), r.differences().get(0).difference());
        assertEquals(d("1000.00"), r.etiUsed());
        assertEquals(d("250.00"), r.etiUnusedAtEnd());
        var codes = r.findings().stream().map(Finding::code).toList();
        assertTrue(codes.contains("EMP201_NOT_FILED"));
        assertTrue(codes.contains("EMP201_MISSING"));
        assertTrue(codes.contains("DIFFERENCE_PAYE"));
    }
}
