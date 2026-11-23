package za.co.handyflow.platform.agriculture.domain.rules;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class AgStockRulesTest {

    private static BigDecimal d(String s) { return new BigDecimal(s); }

    @Test void averageCostWeighsWhatWasOnHand() {
        // 100 kg at R10 plus 300 kg at R14 = R5 200 for 400 kg = R13 a kg
        assertEquals(d("13.00"), AgStockRules.weightedAverageCost(d("100"), d("10.00"), d("300"), d("14.00")));
        assertEquals(d("10.67"), AgStockRules.weightedAverageCost(d("200"), d("10.00"), d("100"), d("12.00")));
    }

    @Test void averageCostIsTheDeliverysCostWhenNothingWasOnHand() {
        assertEquals(d("14.00"), AgStockRules.weightedAverageCost(d("0"), d("10.00"), d("300"), d("14")));
        assertEquals(d("14.00"), AgStockRules.weightedAverageCost(d("50"), null, d("300"), d("14")));
        assertEquals(d("14.00"), AgStockRules.weightedAverageCost(null, null, d("300"), d("14")));
    }

    @Test void noCostWithTheDeliveryLeavesTheCostAlone() {
        assertEquals(d("10.00"), AgStockRules.weightedAverageCost(d("100"), d("10.00"), d("300"), null));
        assertNull(AgStockRules.weightedAverageCost(d("100"), null, d("300"), null));
    }

    @Test void aNegativeCostIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> AgStockRules.weightedAverageCost(d("1"), d("1"), d("1"), d("-1")));
    }

    @Test void adjustmentKeepsItsDirection() {
        assertEquals("ADJUSTMENT_UP", AgStockRules.adjustmentType(d("10"), d("12.5")));
        assertEquals("ADJUSTMENT_DOWN", AgStockRules.adjustmentType(d("10"), d("4")));
        assertEquals(d("2.5"), AgStockRules.adjustmentQuantity(d("10"), d("12.5")));
        assertEquals(d("6"), AgStockRules.adjustmentQuantity(d("10"), d("4")));
        assertThrows(IllegalArgumentException.class, () -> AgStockRules.adjustmentType(d("10"), d("10.000")));
    }

    @Test void adjustmentNeedsAReason() {
        assertEquals("Stock count", AgStockRules.requireReason("  Stock count "));
        assertThrows(IllegalArgumentException.class, () -> AgStockRules.requireReason("no"));
        assertThrows(IllegalArgumentException.class, () -> AgStockRules.requireReason("   "));
        assertThrows(IllegalArgumentException.class, () -> AgStockRules.requireReason(null));
    }

    @Test void anInactiveItemCannotBeReceivedOrIssued() {
        AgStockRules.requireUsable("ACTIVE", "Maize");
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> AgStockRules.requireUsable("INACTIVE", "Maize"));
        assertEquals("Maize is inactive. Reactivate it before receiving or issuing stock", e.getMessage());
    }
}
