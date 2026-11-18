package za.co.handyflow.platform.supplychain.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.supplychain.application.internal.ReplenishmentRules.Input;
import za.co.handyflow.platform.supplychain.application.internal.ReplenishmentRules.Urgency;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ReplenishmentRulesTest {

    private static BigDecimal d(String v) { return new BigDecimal(v); }
    private static Input in(String available, String onOrder, String requested, String rp, String rq, String max, String daily, int lead) {
        return new Input(d(available), d(onOrder), d(requested), d(rp), d(rq), d(max), d(daily), lead);
    }

    @Test void nothingToBaseItOnMeansNoRecommendation() {
        assertThat(ReplenishmentRules.recommend(in("0", "0", "0", "0", "0", "0", "0", 7))).isNull();
    }

    @Test void plentyOfStockNeedsNothing() {
        assertThat(ReplenishmentRules.recommend(in("500", "0", "0", "50", "100", "0", "2", 7))).isNull();
    }

    @Test void anOpenOrderOrRequisitionCountsTowardsWhatItHas() {
        assertThat(ReplenishmentRules.recommend(in("10", "100", "0", "50", "100", "0", "0", 7))).isNull();
        assertThat(ReplenishmentRules.recommend(in("10", "0", "100", "50", "100", "0", "0", 7))).isNull();
    }

    @Test void atTheReorderPointWithoutDemandOrdersTheReorderQuantity() {
        var r = ReplenishmentRules.recommend(in("50", "0", "0", "50", "100", "0", "0", 7));
        assertThat(r.reorderLevel()).isEqualByComparingTo("50");
        assertThat(r.orderQty()).isEqualByComparingTo("100");     // target 150, position 50
        assertThat(r.urgency()).isEqualTo(Urgency.REORDER);
        assertThat(r.reason()).contains("reorder point");
    }

    @Test void fastDemandRaisesTheLevelAboveTheReorderPoint() {
        // 10/day over a 7-day lead time + 3 days' margin needs 100, though the reorder point is only 20
        var r = ReplenishmentRules.recommend(in("60", "0", "0", "20", "50", "0", "10", 7));
        assertThat(r).isNotNull();
        assertThat(r.reorderLevel()).isEqualByComparingTo("100");
        assertThat(r.reason()).contains("recent rate of use");
        assertThat(r.daysCover()).isEqualByComparingTo("6.0");
        assertThat(r.urgency()).isEqualTo(Urgency.CRITICAL);     // 6 days of cover is under the 7-day lead time
    }

    @Test void zeroAvailableIsAStockout() {
        assertThat(ReplenishmentRules.recommend(in("0", "0", "0", "20", "50", "0", "0", 7)).urgency()).isEqualTo(Urgency.STOCKOUT);
        assertThat(ReplenishmentRules.recommend(in("-5", "0", "0", "20", "50", "0", "0", 7)).urgency()).isEqualTo(Urgency.STOCKOUT);
    }

    @Test void aMaximumLevelCapsTheOrder() {
        var r = ReplenishmentRules.recommend(in("10", "0", "0", "20", "50", "200", "0", 7));
        assertThat(r.orderQty()).isEqualByComparingTo("190");     // up to the maximum, not rounded up to a multiple of 50
    }

    @Test void withoutAMaximumTheOrderIsInWholeReorderQuantities() {
        var r = ReplenishmentRules.recommend(in("10", "0", "0", "20", "50", "0", "0", 7));   // target 20 + 50 = 70, short by 60 → 2 x 50
        assertThat(r.orderQty()).isEqualByComparingTo("100");
    }

    @Test void aMonthOfUseSetsTheTargetWhenThatIsMoreThanTheReorderQuantity() {
        var r = ReplenishmentRules.recommend(in("0", "0", "0", "20", "10", "0", "5", 7));    // level 50, target 50 + 150 = 200
        assertThat(r.orderQty()).isEqualByComparingTo("200");
    }

    @Test void unknownLeadTimeFallsBackToTheDefault() {
        var r = ReplenishmentRules.recommend(in("5", "0", "0", "0", "10", "0", "1", 0));      // level = ceil(1 x (7+3)) = 10
        assertThat(r.reorderLevel()).isEqualByComparingTo("10");
    }
}
