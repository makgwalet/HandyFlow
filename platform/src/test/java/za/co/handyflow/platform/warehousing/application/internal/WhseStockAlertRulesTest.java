package za.co.handyflow.platform.warehousing.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.warehousing.application.internal.WhseStockAlertRules.Figures;
import za.co.handyflow.platform.warehousing.application.internal.WhseStockAlertRules.Kind;
import za.co.handyflow.platform.warehousing.application.internal.WhseStockAlertRules.Levels;
import za.co.handyflow.platform.warehousing.application.internal.WhseStockAlertRules.Source;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class WhseStockAlertRulesTest {

    private static BigDecimal bd(String v) { return v == null ? null : new BigDecimal(v); }
    private static Levels levels(String min, String max) { return WhseStockAlertRules.resolve(bd(min), bd(max), null, null); }

    @Test @DisplayName("a warehouse's own levels override the item's, value by value; with neither there is no threshold")
    void resolution() {
        Levels both = WhseStockAlertRules.resolve(bd("100"), bd("500"), bd("50"), bd("250"));
        assertEquals(bd("100"), both.min()); assertEquals(Source.WAREHOUSE, both.minSource());
        assertEquals(bd("500"), both.max()); assertEquals(Source.WAREHOUSE, both.maxSource());

        Levels mixed = WhseStockAlertRules.resolve(bd("30"), null, bd("50"), bd("250"));
        assertEquals(bd("30"), mixed.min()); assertEquals(Source.WAREHOUSE, mixed.minSource());
        assertEquals(bd("250"), mixed.max()); assertEquals(Source.ITEM, mixed.maxSource());

        Levels itemOnly = WhseStockAlertRules.resolve(null, null, bd("50"), null);
        assertEquals(Source.ITEM, itemOnly.minSource()); assertNull(itemOnly.max()); assertNull(itemOnly.maxSource());

        assertFalse(WhseStockAlertRules.resolve(null, null, null, null).any());
        assertTrue(WhseStockAlertRules.classify(WhseStockAlertRules.resolve(null, null, null, null), Figures.of(bd("0"), bd("0"), bd("0"), bd("0"))).isEmpty());
    }

    @Test @DisplayName("100 on the floor is not 100 to sell: reserved, quarantined and damaged stock is kept apart")
    void figures() {
        // 100 sellable on hand, 80 reserved, 15 quarantined, 20 on its way
        Figures f = Figures.of(bd("115"), bd("100"), bd("80"), bd("20"));
        assertEquals(bd("115"), f.physical());
        assertEquals(bd("15"), f.unavailable());
        assertEquals(bd("80"), f.reserved());
        assertEquals(bd("20"), f.available());
        assertEquals(bd("20"), f.incoming());
        assertEquals(bd("100"), f.sellableOnHand());
    }

    @Test @DisplayName("nulls count as zero")
    void nulls() {
        Figures f = Figures.of(null, null, null, null);
        assertEquals(0, f.physical().signum()); assertEquals(0, f.available().signum()); assertEquals(0, f.incoming().signum());
    }

    @Test @DisplayName("below the minimum, with the order that brings it to the maximum less what is already coming")
    void belowMin() {
        var f = WhseStockAlertRules.classify(levels("100", "500"), Figures.of(bd("115"), bd("100"), bd("80"), bd("20"))).orElseThrow();
        assertEquals(Kind.BELOW_MIN, f.kind());
        assertEquals(0, bd("460").compareTo(f.recommendedOrder())); // 500 - 20 available - 20 incoming
    }

    @Test @DisplayName("nothing available with a minimum set is out of stock, not merely below the minimum")
    void outOfStock() {
        var f = WhseStockAlertRules.classify(levels("10", null), Figures.of(bd("5"), bd("0"), bd("0"), bd("0"))).orElseThrow(); // 5 on hand but all quarantined
        assertEquals(Kind.OUT_OF_STOCK, f.kind());
        assertEquals(0, bd("10").compareTo(f.recommendedOrder())); // no maximum: order up to the minimum
        // a zero minimum is not "out of stock" at nothing
        assertTrue(WhseStockAlertRules.classify(levels("0", null), Figures.of(bd("0"), bd("0"), bd("0"), bd("0"))).isEmpty());
        // only a maximum set: nothing is expected, so no out-of-stock alert
        assertTrue(WhseStockAlertRules.classify(levels(null, "100"), Figures.of(bd("0"), bd("0"), bd("0"), bd("0"))).isEmpty());
    }

    @Test @DisplayName("stock that is all reserved is out of stock even though it is on the shelf")
    void reservedIsNotAvailable() {
        var f = WhseStockAlertRules.classify(levels("10", "50"), Figures.of(bd("40"), bd("40"), bd("40"), bd("0"))).orElseThrow();
        assertEquals(Kind.OUT_OF_STOCK, f.kind());
    }

    @Test @DisplayName("incoming stock that covers the shortfall means nothing more to order, but the alert stands")
    void incomingCovers() {
        var f = WhseStockAlertRules.classify(levels("100", "200"), Figures.of(bd("20"), bd("20"), bd("0"), bd("500"))).orElseThrow();
        assertEquals(Kind.BELOW_MIN, f.kind());
        assertEquals(0, f.recommendedOrder().signum());
    }

    @Test @DisplayName("above the maximum on sellable stock, with no order to place")
    void aboveMax() {
        var f = WhseStockAlertRules.classify(levels(null, "100"), Figures.of(bd("150"), bd("150"), bd("0"), bd("0"))).orElseThrow();
        assertEquals(Kind.ABOVE_MAX, f.kind());
        assertNull(f.recommendedOrder());
        // quarantined stock does not count towards overstock
        assertTrue(WhseStockAlertRules.classify(levels(null, "100"), Figures.of(bd("150"), bd("90"), bd("0"), bd("0"))).isEmpty());
    }

    @Test @DisplayName("exactly at the minimum or maximum is fine")
    void boundaries() {
        assertTrue(WhseStockAlertRules.classify(levels("100", "500"), Figures.of(bd("100"), bd("100"), bd("0"), bd("0"))).isEmpty());
        assertTrue(WhseStockAlertRules.classify(levels("100", "500"), Figures.of(bd("500"), bd("500"), bd("0"), bd("0"))).isEmpty());
    }

    @Test @DisplayName("a threshold needs a level, sane amounts and a maximum above the minimum")
    void problems() {
        assertNull(WhseStockAlertRules.problem(bd("30"), bd("150")));
        assertNull(WhseStockAlertRules.problem(null, bd("150")));
        assertNull(WhseStockAlertRules.problem(bd("0"), null));
        assertNotNull(WhseStockAlertRules.problem(null, null));
        assertNotNull(WhseStockAlertRules.problem(bd("-1"), null));
        assertNotNull(WhseStockAlertRules.problem(null, bd("0")));
        assertNotNull(WhseStockAlertRules.problem(bd("150"), bd("150")));
        assertNotNull(WhseStockAlertRules.problem(bd("10"), bd("5")));
        assertNotNull(WhseStockAlertRules.problem(bd("1.2345"), null));
        assertNotNull(WhseStockAlertRules.problem(null, bd("1000000000")));
    }

    @Test @DisplayName("a blank warehouse level cannot leave the item's default on the wrong side of it")
    void afterDefaults() {
        assertNull(WhseStockAlertRules.problemAfterDefaults(bd("30"), bd("150"), bd("500"), bd("600")));   // both set here: item defaults unused
        assertNotNull(WhseStockAlertRules.problemAfterDefaults(bd("300"), null, bd("50"), bd("250")));     // min 300 over the item's max 250
        assertNotNull(WhseStockAlertRules.problemAfterDefaults(null, bd("20"), bd("50"), bd("250")));      // max 20 under the item's min 50
        assertNull(WhseStockAlertRules.problemAfterDefaults(bd("30"), null, bd("50"), bd("250")));
    }
}
