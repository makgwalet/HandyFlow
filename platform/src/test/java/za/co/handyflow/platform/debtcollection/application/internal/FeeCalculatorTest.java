package za.co.handyflow.platform.debtcollection.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.debtcollection.application.internal.FeeCalculator.Terms;
import za.co.handyflow.platform.debtcollection.domain.model.FeeCalcMethod;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeeCalculatorTest {

    private static BigDecimal d(String s) { return new BigDecimal(s); }

    private static Terms fixed(String amt, String perCase, Integer perMonth) {
        return new Terms(FeeCalcMethod.FIXED, d(amt), null, perCase == null ? null : d(perCase), perMonth);
    }

    @Test
    void fixedFee() {
        var r = FeeCalculator.compute(fixed("3.50", null, null), d("1000"), BigDecimal.ZERO, 0);
        assertEquals(d("3.50"), r.amount());
        assertFalse(r.capped());
    }

    @Test
    void percentageFeeRoundsHalfUp() {
        var t = new Terms(FeeCalcMethod.PERCENT_OF_OUTSTANDING, null, d("5"), null, null);
        assertEquals(d("50.00"), FeeCalculator.compute(t, d("1000.00"), BigDecimal.ZERO, 0).amount());
        assertEquals(d("0.17"), FeeCalculator.compute(t, d("3.33"), BigDecimal.ZERO, 0).amount()); // 0.1665 -> 0.17
    }

    @Test
    void monthlyLimitStopsFurtherCharges() {
        Terms t = fixed("3.50", null, 10);
        assertEquals(d("3.50"), FeeCalculator.compute(t, d("100"), BigDecimal.ZERO, 9).amount());
        assertThrows(IllegalStateException.class, () -> FeeCalculator.compute(t, d("100"), BigDecimal.ZERO, 10));
    }

    @Test
    void perCaseCapTrimsThenRefuses() {
        Terms t = fixed("100.00", "250.00", null);
        assertEquals(d("100.00"), FeeCalculator.compute(t, d("1"), d("100.00"), 0).amount());
        var trimmed = FeeCalculator.compute(t, d("1"), d("200.00"), 0);
        assertEquals(d("50.00"), trimmed.amount());
        assertTrue(trimmed.capped());
        assertThrows(IllegalStateException.class, () -> FeeCalculator.compute(t, d("1"), d("250.00"), 0));
    }

    @Test
    void zeroFeeIsRefused() {
        var t = new Terms(FeeCalcMethod.PERCENT_OF_OUTSTANDING, null, d("1"), null, null);
        assertThrows(IllegalStateException.class, () -> FeeCalculator.compute(t, d("0.10"), BigDecimal.ZERO, 0)); // 0.001 -> 0.00
    }
}
