package za.co.handyflow.platform.complianceservices.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PricingRulesTest {

    private static final BigDecimal VAT = new BigDecimal("15");

    private static BigDecimal d(String s) { return new BigDecimal(s); }

    private static PricingRules.Line line(String model, String base, String unit, String hourly, String min, String urgent, String tax) {
        return new PricingRules.Line(model, base == null ? null : d(base), unit == null ? null : d(unit), hourly == null ? null : d(hourly),
                min == null ? null : d(min), urgent == null ? null : d(urgent), tax);
    }

    private static PricingRules.Inputs in(String units, String hours, String complexity, boolean urgent, List<PricingRules.Extra> extras,
                                          List<PricingRules.Disbursement> disb, String discount) {
        return new PricingRules.Inputs(units == null ? null : d(units), hours == null ? null : d(hours), complexity == null ? null : d(complexity),
                urgent, extras, disb, discount == null ? null : d(discount));
    }

    @Test @DisplayName("fixed R2,000 with 20% complexity is R2,400 before VAT")
    void fixedWithComplexity() {
        var r = PricingRules.price(line("FIXED", "2000", null, null, null, null, "STANDARD"), in(null, null, "20", false, null, null, null), VAT);
        assertThat(r.professional()).isEqualByComparingTo("2400.00");
        assertThat(r.vat()).isEqualByComparingTo("360.00");
        assertThat(r.total()).isEqualByComparingTo("2760.00");
    }

    @Test @DisplayName("multi-site OHS: R3,000 plus 3 sites at R800 is R5,400, and R600 travel is a disbursement, R6,000 before VAT")
    void perSiteWithDisbursement() {
        var r = PricingRules.price(line("PER_SITE", "3000", "800", null, null, null, "STANDARD"),
                in("3", null, null, false, null, List.of(new PricingRules.Disbursement("Travel", d("600"), true)), null), VAT);
        assertThat(r.professional()).isEqualByComparingTo("5400.00");
        assertThat(r.disbursements()).isEqualByComparingTo("600.00");
        assertThat(r.professional().add(r.disbursements())).isEqualByComparingTo("6000.00");
        assertThat(r.vat()).isEqualByComparingTo("900.00");
    }

    @Test @DisplayName("retainer R2,500 plus 2 extra verifications at R150 is R2,800")
    void retainerWithExtras() {
        var r = PricingRules.price(line("RETAINER", "2500", null, null, null, null, "STANDARD"),
                in(null, null, null, false, List.of(new PricingRules.Extra("verification", d("2"), d("150"))), null, null), VAT);
        assertThat(r.professional()).isEqualByComparingTo("2800.00");
    }

    @Test @DisplayName("urgent R2,000 plus 25% is R2,500")
    void urgent() {
        var r = PricingRules.price(line("FIXED", "2000", null, null, null, "25", "STANDARD"), in(null, null, null, true, null, null, null), VAT);
        assertThat(r.professional()).isEqualByComparingTo("2500.00");
    }

    @Test @DisplayName("a package of R5,000 less 10% is R4,500, with VAT added after the discount")
    void discountThenVat() {
        var r = PricingRules.price(line("FIXED", "5000", null, null, null, null, "STANDARD"), in(null, null, null, false, null, null, "10"), VAT);
        assertThat(r.discount()).isEqualByComparingTo("500.00");
        assertThat(r.professional()).isEqualByComparingTo("4500.00");
        assertThat(r.vat()).isEqualByComparingTo("675.00");
        assertThat(r.total()).isEqualByComparingTo("5175.00");
        assertThat(r.discountApprover()).isEqualTo("COMPLIANCE_MANAGER");
    }

    @Test @DisplayName("hourly prices hours at the grade's rate, and the minimum fee lifts a small job")
    void hourlyAndMinimum() {
        var l = line("HOURLY", null, null, "850", "1000", null, "STANDARD");
        assertThat(PricingRules.price(l, in(null, "4", null, false, null, null, null), VAT).professional()).isEqualByComparingTo("3400.00");
        var small = PricingRules.price(l, in(null, "0.5", null, false, null, null, null), VAT);
        assertThat(small.professional()).isEqualByComparingTo("1000.00");
        assertThatThrownBy(() -> PricingRules.price(l, in(null, null, null, false, null, null, null), VAT)).hasMessageContaining("hours");
    }

    @Test @DisplayName("zero-rated and exempt lines carry no VAT on the fee, and only vatable disbursements carry it")
    void taxTreatments() {
        var disb = List.of(new PricingRules.Disbursement("CIPC fee", d("100"), false), new PricingRules.Disbursement("Courier", d("200"), true));
        var zero = PricingRules.price(line("FIXED", "1000", null, null, null, null, "ZERO_RATED"), in(null, null, null, false, null, disb, null), VAT);
        assertThat(zero.vat()).isEqualByComparingTo("30.00");
        assertThat(zero.total()).isEqualByComparingTo("1330.00");
        var exempt = PricingRules.price(line("FIXED", "1000", null, null, null, null, "EXEMPT"), in(null, null, null, false, null, null, null), VAT);
        assertThat(exempt.vat()).isEqualByComparingTo("0.00");
    }

    @Test @DisplayName("disbursements are not touched by complexity, surcharge or discount")
    void disbursementsPassedOn() {
        var r = PricingRules.price(line("FIXED", "1000", null, null, null, "50", "STANDARD"),
                in(null, null, "10", true, null, List.of(new PricingRules.Disbursement("Fee", d("500"), false)), "10"), VAT);
        assertThat(r.disbursements()).isEqualByComparingTo("500.00");
        assertThat(r.professional()).isEqualByComparingTo("1485.00");
    }

    @Test @DisplayName("what the book does not say is refused rather than guessed")
    void missingPieces() {
        var fixed = line("FIXED", "1000", null, null, null, null, "STANDARD");
        assertThatThrownBy(() -> PricingRules.price(fixed, in(null, null, null, true, null, null, null), VAT)).hasMessageContaining("urgent");
        assertThatThrownBy(() -> PricingRules.price(line("PER_SITE", null, null, null, null, null, "STANDARD"), in("2", null, null, false, null, null, null), VAT)).hasMessageContaining("per unit");
        assertThatThrownBy(() -> PricingRules.price(line("PER_SITE", null, "800", null, null, null, "STANDARD"), in(null, null, null, false, null, null, null), VAT)).hasMessageContaining("units");
        assertThatThrownBy(() -> PricingRules.price(fixed, in(null, null, "250", false, null, null, null), VAT)).hasMessageContaining("Complexity");
        assertThatThrownBy(() -> PricingRules.price(fixed, in(null, null, null, false, null, null, "101"), VAT)).hasMessageContaining("Discount");
    }

    @Test @DisplayName("a hybrid adds base fee, units and hours")
    void hybrid() {
        var l = line("HYBRID", "1000", "200", "500", null, null, "STANDARD");
        assertThat(PricingRules.price(l, in("2", "3", null, false, null, null, null), VAT).professional()).isEqualByComparingTo("2900.00");
    }

    @Test @DisplayName("cost R1,600 at a 40% margin is R2,666.67, quoted at R2,700 for a 40.7% margin")
    void margin() {
        BigDecimal price = PricingRules.priceForMargin(d("1600"), d("40"));
        assertThat(price).isEqualByComparingTo("2666.67");
        BigDecimal quote = PricingRules.roundUpTo(price, d("50"));
        assertThat(quote).isEqualByComparingTo("2700.00");
        assertThat(PricingRules.grossMargin(quote, d("1600"))).isEqualByComparingTo("40.74");
        assertThat(PricingRules.grossMargin(BigDecimal.ZERO, d("1"))).isNull();
        assertThatThrownBy(() -> PricingRules.priceForMargin(d("1"), d("96"))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test @DisplayName("cost adds labour, an overhead share of labour and external costs")
    void cost() {
        assertThat(PricingRules.cost(d("4"), d("250"), d("100"), d("10"))).isEqualByComparingTo("1200.00");
        assertThat(PricingRules.cost(null, null, null, null)).isEqualByComparingTo("0.00");
    }

    @Test @DisplayName("discount authority follows the proposed thresholds")
    void approver() {
        assertThat(PricingRules.discountApprover(d("5"))).isEqualTo("CONSULTANT");
        assertThat(PricingRules.discountApprover(d("5.01"))).isEqualTo("COMPLIANCE_MANAGER");
        assertThat(PricingRules.discountApprover(d("10"))).isEqualTo("COMPLIANCE_MANAGER");
        assertThat(PricingRules.discountApprover(d("15"))).isEqualTo("PRACTICE_OWNER");
        assertThat(PricingRules.discountApprover(d("15.01"))).isEqualTo("PRACTICE_OWNER_WITH_EXCEPTION");
    }

    @Test @DisplayName("input checks: money, percent, grade, tax treatment, line gaps")
    void inputs() {
        assertThat(PricingRules.money(d("10.005"), "x")).isEqualByComparingTo("10.01");
        assertThatThrownBy(() -> PricingRules.money(d("-1"), "Fee")).hasMessageContaining("Fee");
        assertThat(PricingRules.grade(" senior consultant ")).isEqualTo("SENIOR_CONSULTANT");
        assertThatThrownBy(() -> PricingRules.grade("a")).isInstanceOf(IllegalArgumentException.class);
        assertThat(PricingRules.taxTreatment(null)).isEqualTo("STANDARD");
        assertThatThrownBy(() -> PricingRules.taxTreatment("FREE")).isInstanceOf(IllegalArgumentException.class);
        assertThat(PricingRules.lineGaps("FIXED", null, null, null)).containsExactly("a base fee");
        assertThat(PricingRules.lineGaps("HOURLY", null, null, "SENIOR")).isEmpty();
        assertThat(PricingRules.lineGaps("PER_SITE", null, null, null)).containsExactly("a rate per unit");
    }
}
