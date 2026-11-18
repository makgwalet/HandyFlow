package za.co.handyflow.platform.supplychain.application.internal;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AnalyticsRulesTest {
    private static BigDecimal d(String v) { return new BigDecimal(v); }

    @Test void shareIsAPercentageToOneDecimalAndNullWithoutATotal() {
        assertThat(AnalyticsRules.sharePct(d("25"), d("200"))).isEqualTo(12.5);
        assertThat(AnalyticsRules.sharePct(d("1"), d("3"))).isEqualTo(33.3);
        assertThat(AnalyticsRules.sharePct(d("10"), BigDecimal.ZERO)).isNull();
        assertThat(AnalyticsRules.sharePct(null, d("10"))).isNull();
    }

    @Test void topShareTakesTheBiggestN() {
        assertThat(AnalyticsRules.topShare(List.of(d("50"), d("30"), d("10"), d("5"), d("3"), d("2")), 3, d("100"))).isEqualTo(90.0);
        assertThat(AnalyticsRules.topShare(List.of(), 5, BigDecimal.ZERO)).isNull();
    }

    @Test void savingNeedsAPositiveBaselineAndCanBeNegative() {
        assertThat(AnalyticsRules.saving(d("1000"), d("900"))).isEqualByComparingTo("100");
        assertThat(AnalyticsRules.saving(d("1000"), d("1100"))).isEqualByComparingTo("-100");
        assertThat(AnalyticsRules.saving(null, d("900"))).isNull();
        assertThat(AnalyticsRules.saving(BigDecimal.ZERO, d("900"))).isNull();
    }

    @Test void supplierFlags() {
        assertThat(AnalyticsRules.supplierFlag(null, 40.0)).as("no data is not poor performance").isNull();
        assertThat(AnalyticsRules.supplierFlag(85, 40.0)).isNull();
        assertThat(AnalyticsRules.supplierFlag(60, 40.0)).as("60 is not low").isNull();
        assertThat(AnalyticsRules.supplierFlag(45, 2.0)).isEqualTo("LOW_SCORE");
        assertThat(AnalyticsRules.supplierFlag(45, null)).isEqualTo("LOW_SCORE");
        assertThat(AnalyticsRules.supplierFlag(45, 5.0)).isEqualTo("HIGH_SPEND_LOW_SCORE");
        assertThat(AnalyticsRules.supplierFlag(45, 30.0)).isEqualTo("HIGH_SPEND_LOW_SCORE");
    }

    @Test void overContractOnlyCountsPaymentAboveTheAgreedPrice() {
        assertThat(AnalyticsRules.overContract(d("110"), d("100"), d("8"))).isEqualByComparingTo("80.00");
        assertThat(AnalyticsRules.overContract(d("100"), d("100"), d("8"))).isEqualByComparingTo("0");
        assertThat(AnalyticsRules.overContract(d("90"), d("100"), d("8"))).isEqualByComparingTo("0");
        assertThat(AnalyticsRules.overContract(null, d("100"), d("8"))).isEqualByComparingTo("0");
    }
}
