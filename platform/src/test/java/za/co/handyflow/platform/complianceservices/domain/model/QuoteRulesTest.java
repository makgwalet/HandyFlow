package za.co.handyflow.platform.complianceservices.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuoteRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2027, 3, 1);

    private static BigDecimal d(String s) { return new BigDecimal(s); }

    @Test @DisplayName("the strictest discount on any line decides who must approve")
    void requiredApprover() {
        assertThat(QuoteRules.requiredApprover(List.of())).isEqualTo("CONSULTANT");
        assertThat(QuoteRules.requiredApprover(List.of(d("0"), d("5")))).isEqualTo("CONSULTANT");
        assertThat(QuoteRules.requiredApprover(List.of(d("5"), d("10"), d("2")))).isEqualTo("COMPLIANCE_MANAGER");
        assertThat(QuoteRules.requiredApprover(List.of(d("12"), d("10")))).isEqualTo("PRACTICE_OWNER");
        assertThat(QuoteRules.requiredApprover(List.of(d("16"), d("1")))).isEqualTo("PRACTICE_OWNER_WITH_EXCEPTION");
        assertThat(QuoteRules.needsApproval("CONSULTANT")).isFalse();
        assertThat(QuoteRules.needsApproval("PRACTICE_OWNER")).isTrue();
    }

    @Test @DisplayName("totals add up the lines and keep the VAT rate")
    void sum() {
        var t = QuoteRules.sum(List.of(
                new QuoteRules.Priced(d("4500"), d("500"), d("0"), d("675"), d("5175"), d("15")),
                new QuoteRules.Priced(d("5400"), d("0"), d("600"), d("900"), d("6900"), d("15"))));
        assertThat(t.professional()).isEqualByComparingTo("9900.00");
        assertThat(t.discount()).isEqualByComparingTo("500.00");
        assertThat(t.disbursements()).isEqualByComparingTo("600.00");
        assertThat(t.vat()).isEqualByComparingTo("1575.00");
        assertThat(t.total()).isEqualByComparingTo("12075.00");
        assertThat(t.vatRatePct()).isEqualByComparingTo("15");
        assertThat(QuoteRules.sum(List.of()).total()).isEqualByComparingTo("0.00");
    }

    @Test @DisplayName("a quote stands for 30 days by default and at most a year")
    void validity() {
        assertThat(QuoteRules.defaultValidUntil(TODAY)).isEqualTo(LocalDate.of(2027, 3, 31));
        assertThat(QuoteRules.validUntilProblem(null, TODAY)).contains("how long");
        assertThat(QuoteRules.validUntilProblem(TODAY.minusDays(1), TODAY)).contains("passed");
        assertThat(QuoteRules.validUntilProblem(TODAY, TODAY)).isNull();
        assertThat(QuoteRules.validUntilProblem(TODAY.plusDays(365), TODAY)).isNull();
        assertThat(QuoteRules.validUntilProblem(TODAY.plusDays(366), TODAY)).contains("365");
    }

    @Test @DisplayName("a sent quote past its date has expired; nothing else does")
    void expiry() {
        assertThat(QuoteRules.effectiveStatus("SENT", TODAY.minusDays(1), TODAY)).isEqualTo("EXPIRED");
        assertThat(QuoteRules.effectiveStatus("SENT", TODAY, TODAY)).isEqualTo("SENT");
        assertThat(QuoteRules.effectiveStatus("ACCEPTED", TODAY.minusDays(30), TODAY)).isEqualTo("ACCEPTED");
        assertThat(QuoteRules.effectiveStatus("DRAFT", null, TODAY)).isEqualTo("DRAFT");
    }

    @Test @DisplayName("a note needs real words and is trimmed to its limit")
    void note() {
        assertThat(QuoteRules.note("  Accepted by email  ", "How", 100)).isEqualTo("Accepted by email");
        assertThat(QuoteRules.note("x".repeat(50), "How", 20).length()).isEqualTo(20);
        assertThatThrownBy(() -> QuoteRules.note("ok", "How the client accepted", 100)).hasMessageContaining("How the client accepted");
        assertThatThrownBy(() -> QuoteRules.note(null, "Reason", 100)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test @DisplayName("a breakdown is stored and read back, and an unreadable row is left out")
    void breakdown() {
        var parts = List.of(new PricingRules.Part("Fee", d("2000.00")), new PricingRules.Part("Discount 10%", d("-200.00")), new PricingRules.Part("Disbursement: CIPC\tfee", d("100.00")));
        var back = QuoteRules.parseBreakdown(QuoteRules.breakdownText(parts));
        assertThat(back).hasSize(3);
        assertThat(back.get(1).label()).isEqualTo("Discount 10%");
        assertThat(back.get(1).amount()).isEqualByComparingTo("-200.00");
        assertThat(back.get(2).label()).isEqualTo("Disbursement: CIPC fee");
        assertThat(QuoteRules.parseBreakdown("junk\nFee\tnotanumber\nOk\t5\n")).hasSize(1);
        assertThat(QuoteRules.parseBreakdown(null)).isEmpty();
    }
}
