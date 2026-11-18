package za.co.handyflow.platform.supplychain.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.supplychain.application.internal.PriceOverrideRules.Assessment;
import za.co.handyflow.platform.supplychain.application.internal.PriceOverrideRules.Kind;
import za.co.handyflow.platform.supplychain.domain.model.ScPoLine;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PriceOverrideRulesTest {

    private static BigDecimal d(String s) { return new BigDecimal(s); }

    @Test
    void samePriceNeedsNothing() {
        Assessment a = PriceOverrideRules.assess(d("100"), d("100.00"), d("100"), d("5"));
        assertThat(a.kind()).isEqualTo(Kind.UNCHANGED);
        assertThat(a.needsApproval()).isFalse();
        assertThat(PriceOverrideRules.reasonProblem(a, null, null)).isNull();
    }

    @Test
    void aLowerPriceIsASavingOfTheDifferenceTimesQuantity() {          // the R100 -> R95 x 100 example: R500
        Assessment a = PriceOverrideRules.assess(d("100"), d("95"), d("100"), d("5"));
        assertThat(a.kind()).isEqualTo(Kind.DECREASE);
        assertThat(a.saving()).isEqualByComparingTo("500.00");
        assertThat(a.needsApproval()).isFalse();
        assertThat(PriceOverrideRules.reasonProblem(a, null, null)).contains("reason");   // still needs a reason
    }

    @Test
    void anIncreaseWithinToleranceIsAllowedWithoutSecondApproval_andTheBoundaryIsInclusive() {
        assertThat(PriceOverrideRules.assess(d("100"), d("105"), d("10"), d("5")).kind()).isEqualTo(Kind.INCREASE_WITHIN_TOLERANCE);
        assertThat(PriceOverrideRules.assess(d("100"), d("105"), d("10"), d("5")).needsApproval()).isFalse();
    }

    @Test
    void anIncreaseBeyondToleranceNeedsApproval() {                   // the R100 -> R120 example: +20%
        Assessment a = PriceOverrideRules.assess(d("100"), d("120"), d("100"), d("5"));
        assertThat(a.kind()).isEqualTo(Kind.INCREASE_BEYOND_TOLERANCE);
        assertThat(a.changePct()).isEqualByComparingTo("20.00");
        assertThat(a.needsApproval()).isTrue();
        assertThat(a.saving()).isEqualByComparingTo("0");
    }

    @Test
    void aZeroEstimateWithAPriceAlwaysNeedsApproval() {
        Assessment a = PriceOverrideRules.assess(d("0"), d("10"), d("1"), d("100"));
        assertThat(a.needsApproval()).isTrue();
        assertThat(a.changePct()).isNull();
    }

    @Test
    void zeroToleranceMeansAnyIncreaseNeedsApproval() {
        assertThat(PriceOverrideRules.assess(d("100"), d("100.01"), d("1"), d("0")).needsApproval()).isTrue();
    }

    @Test
    void reasonsAreValidated() {
        Assessment a = PriceOverrideRules.assess(d("100"), d("95"), d("1"), d("5"));
        assertThat(PriceOverrideRules.reasonProblem(a, "SUPPLIER_QUOTATION", null)).isNull();
        assertThat(PriceOverrideRules.reasonProblem(a, "BECAUSE", null)).contains("Unknown");
        assertThat(PriceOverrideRules.reasonProblem(a, "OTHER", " ")).contains("note");
        assertThat(PriceOverrideRules.reasonProblem(a, "OTHER", "Bulk deal")).isNull();
    }

    @Test
    void aReceiverCannotChangeThePoPrice_butMayRepeatIt() {
        ScPoLine line = ScPoLine.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "Bolts", null,
                BigDecimal.TEN, d("100"), BigDecimal.ZERO);
        assertThatCode(() -> GoodsReceivingService.requirePoPrice(null, line)).doesNotThrowAnyException();
        assertThatCode(() -> GoodsReceivingService.requirePoPrice(d("100.00"), line)).doesNotThrowAnyException();
        assertThatThrownBy(() -> GoodsReceivingService.requirePoPrice(d("115"), line))
                .isInstanceOf(HandyFlowException.class)
                .extracting(e -> ((HandyFlowException) e).getErrorCode()).isEqualTo("RECEIVER_COST_OVERRIDE_REFUSED");
        assertThatThrownBy(() -> GoodsReceivingService.requirePoPrice(d("90"), line)).isInstanceOf(HandyFlowException.class);
    }
}
