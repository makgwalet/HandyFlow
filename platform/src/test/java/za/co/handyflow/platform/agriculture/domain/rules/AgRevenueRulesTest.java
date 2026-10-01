package za.co.handyflow.platform.agriculture.domain.rules;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AgRevenueRulesTest {

    private static BigDecimal bd(String s) { return new BigDecimal(s); }
    private static List<BigDecimal> list(String... s) { List<BigDecimal> l = new ArrayList<>(); for (String x : s) l.add(bd(x)); return l; }
    private static void assertNumber(String expected, BigDecimal actual) { assertEquals(0, bd(expected).compareTo(actual), "expected " + expected + " but was " + actual); }
    private static BigDecimal sum(List<BigDecimal> l) { BigDecimal t = BigDecimal.ZERO; for (BigDecimal x : l) t = t.add(x); return t; }

    @Test
    @DisplayName("revenue counts from issue onwards; drafts and cancelled invoices never do")
    void recognisedStatuses() {
        for (String s : List.of("ISSUED", "PARTIALLY_PAID", "PAID", "OVERPAID", "OVERDUE")) assertTrue(AgRevenueRules.isRecognised(s), s);
        for (String s : List.of("DRAFT", "CANCELLED", "SOMETHING_NEW", "", "issued")) assertFalse(AgRevenueRules.isRecognised(s), s);
        assertFalse(AgRevenueRules.isRecognised(null));
    }

    @Test
    @DisplayName("credit ratio is the credited share of the invoice, capped at 1, and 0 when nothing is credited")
    void creditRatio() {
        assertNumber("0.25", AgRevenueRules.creditRatio(bd("500"), bd("2000")));
        assertNumber("1", AgRevenueRules.creditRatio(bd("2000"), bd("2000")));
        assertNumber("1", AgRevenueRules.creditRatio(bd("2500"), bd("2000")));          // a credit larger than the invoice is a full credit
        assertNumber("0", AgRevenueRules.creditRatio(null, bd("2000")));
        assertNumber("0", AgRevenueRules.creditRatio(BigDecimal.ZERO, bd("2000")));
        assertNumber("0", AgRevenueRules.creditRatio(bd("100"), BigDecimal.ZERO));      // no invoice subtotal to take a share of
        assertNumber("0.333333", AgRevenueRules.creditRatio(bd("1"), bd("3")));
    }

    @Test
    @DisplayName("a line keeps its proportional share of the invoice's credit notes, to the cent")
    void netLineRevenue() {
        assertNumber("750.00", AgRevenueRules.netLineRevenue(bd("1000"), bd("500"), bd("2000")));       // a quarter of the invoice was credited
        assertNumber("1000.00", AgRevenueRules.netLineRevenue(bd("1000"), null, bd("2000")));
        assertNumber("0.00", AgRevenueRules.netLineRevenue(bd("1000"), bd("2000"), bd("2000")));
        assertNumber("0.00", AgRevenueRules.netLineRevenue(bd("1000"), bd("9999"), bd("2000")));
        assertNumber("666.67", AgRevenueRules.netLineRevenue(bd("1000"), bd("1"), bd("3")));            // 1000 x (1 - 0.333333)
        assertNumber("0.00", AgRevenueRules.netLineRevenue(null, bd("1"), bd("3")));
        assertEquals(2, AgRevenueRules.netLineRevenue(bd("1000"), null, bd("2000")).scale());
    }

    @Test
    @DisplayName("what is left to allocate is never negative")
    void remainingQuantity() {
        assertNumber("6", AgRevenueRules.remainingQuantity(bd("10"), bd("4")));
        assertNumber("0", AgRevenueRules.remainingQuantity(bd("10"), bd("10")));
        assertNumber("0", AgRevenueRules.remainingQuantity(bd("10"), bd("12")));
        assertNumber("10", AgRevenueRules.remainingQuantity(bd("10"), null));
    }

    @Test
    @DisplayName("an allocation cannot exceed what is left on the line, within a rounding tolerance")
    void requireWithinLine() {
        assertDoesNotThrow(() -> AgRevenueRules.requireWithinLine(bd("10"), bd("4"), bd("6")));
        assertDoesNotThrow(() -> AgRevenueRules.requireWithinLine(bd("10"), bd("4"), bd("6.0004")));      // inside the tolerance
        IllegalArgumentException over = assertThrows(IllegalArgumentException.class, () -> AgRevenueRules.requireWithinLine(bd("10"), bd("4"), bd("6.001")));
        assertTrue(over.getMessage().contains("only 6 is left"), over.getMessage());
        assertThrows(IllegalArgumentException.class, () -> AgRevenueRules.requireWithinLine(bd("10"), bd("10"), bd("0.01")));
        assertThrows(IllegalArgumentException.class, () -> AgRevenueRules.requireWithinLine(bd("10"), null, BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> AgRevenueRules.requireWithinLine(bd("10"), null, bd("-1")));
        assertThrows(IllegalArgumentException.class, () -> AgRevenueRules.requireWithinLine(bd("10"), null, null));
    }

    @Test
    @DisplayName("revenue is shared in proportion to quantity and adds up exactly")
    void apportionProportionally() {
        List<BigDecimal> parts = AgRevenueRules.apportion(bd("2100.00"), bd("2100"), list("1000", "1100"));
        assertNumber("1000.00", parts.get(0));
        assertNumber("1100.00", parts.get(1));
        assertNumber("2100.00", sum(parts));
    }

    @Test
    @DisplayName("the leftover cent goes to the largest remainder, ties to the earlier allocation")
    void apportionRounding() {
        List<BigDecimal> thirds = AgRevenueRules.apportion(bd("100.00"), bd("3"), list("1", "1", "1"));
        assertNumber("100.00", sum(thirds));
        assertNumber("33.34", thirds.get(0));
        assertNumber("33.33", thirds.get(1));
        assertNumber("33.33", thirds.get(2));
        List<BigDecimal> uneven = AgRevenueRules.apportion(bd("0.10"), bd("100"), list("33", "33", "34"));
        assertNumber("0.03", uneven.get(0));
        assertNumber("0.03", uneven.get(1));
        assertNumber("0.04", uneven.get(2));
    }

    @Test
    @DisplayName("a line allocated only in part yields exactly that part of its revenue")
    void apportionPartialAllocation() {
        List<BigDecimal> parts = AgRevenueRules.apportion(bd("1000.00"), bd("10"), list("4"));
        assertNumber("400.00", parts.get(0));
        List<BigDecimal> two = AgRevenueRules.apportion(bd("100.00"), bd("3"), list("1", "1"));             // two thirds allocated: R66.67 in total
        assertNumber("66.67", sum(two));
    }

    @Test
    @DisplayName("allocating a hair more than the line (within tolerance) never pays out more than the line earned")
    void apportionNeverExceedsTheLine() {
        List<BigDecimal> parts = AgRevenueRules.apportion(bd("500.00"), bd("10"), list("10.0004"));
        assertTrue(sum(parts).compareTo(bd("500.00")) <= 0, "paid out " + sum(parts));
    }

    @Test
    @DisplayName("nothing to share gives zeros, and no allocations gives an empty list")
    void apportionEdges() {
        for (BigDecimal b : AgRevenueRules.apportion(BigDecimal.ZERO, bd("10"), list("5", "5"))) assertNumber("0", b);
        for (BigDecimal b : AgRevenueRules.apportion(bd("100"), BigDecimal.ZERO, list("5"))) assertNumber("0", b);
        for (BigDecimal b : AgRevenueRules.apportion(null, bd("10"), list("5"))) assertNumber("0", b);
        assertTrue(AgRevenueRules.apportion(bd("100"), bd("10"), List.of()).isEmpty());
        assertEquals(2, AgRevenueRules.apportion(bd("100"), bd("10"), list("5")).get(0).scale());
    }

    @Test
    @DisplayName("many awkward allocations still add up exactly to the allocated share")
    void apportionAlwaysExact() {
        long seed = 12345;
        for (int round = 0; round < 200; round++) {
            seed = (seed * 1103515245L + 12345L) & 0x7fffffffL;
            int n = 1 + (int) (seed % 6);
            List<BigDecimal> qtys = new ArrayList<>();
            BigDecimal total = BigDecimal.ZERO;
            for (int i = 0; i < n; i++) {
                seed = (seed * 1103515245L + 12345L) & 0x7fffffffL;
                BigDecimal q = BigDecimal.valueOf(1 + (seed % 900), 1);                        // 0.1 .. 90.0
                qtys.add(q);
                total = total.add(q);
            }
            seed = (seed * 1103515245L + 12345L) & 0x7fffffffL;
            BigDecimal revenue = BigDecimal.valueOf(100 + (seed % 9_000_000), 2);
            List<BigDecimal> parts = AgRevenueRules.apportion(revenue, total, qtys);
            assertEquals(0, revenue.compareTo(sum(parts)), "revenue " + revenue + " over " + qtys + " gave " + parts);
        }
    }
}
