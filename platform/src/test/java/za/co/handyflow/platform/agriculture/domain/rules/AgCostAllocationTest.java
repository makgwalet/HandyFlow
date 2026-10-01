package za.co.handyflow.platform.agriculture.domain.rules;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.agriculture.domain.rules.AgCostAllocation.Part;
import za.co.handyflow.platform.agriculture.domain.rules.AgCostAllocation.Share;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AgCostAllocationTest {

    private static final UUID A = UUID.randomUUID(), B = UUID.randomUUID(), C = UUID.randomUUID();

    private static BigDecimal bd(String s) { return new BigDecimal(s); }
    private static Share share(String type, UUID id, String pct) { return new Share(type, id, bd(pct)); }
    private static void assertNumber(String expected, BigDecimal actual) { assertEquals(0, bd(expected).compareTo(actual), "expected " + expected + " but was " + actual); }

    private static BigDecimal sum(List<Part> parts) {
        BigDecimal t = BigDecimal.ZERO;
        for (Part p : parts) t = t.add(p.amount());
        return t;
    }

    @Test
    @DisplayName("a single target takes the whole amount")
    void singleTarget() {
        List<Part> parts = AgCostAllocation.split(bd("1250.50"), List.of(share("CROP_CYCLE", A, "100")));
        assertEquals(1, parts.size());
        assertNumber("1250.50", parts.get(0).amount());
        assertEquals("CROP_CYCLE", parts.get(0).targetType());
        assertEquals(A, parts.get(0).targetId());
    }

    @Test
    @DisplayName("an even split by percentage")
    void evenSplit() {
        List<Part> parts = AgCostAllocation.split(bd("1000"), List.of(share("CROP_CYCLE", A, "60"), share("GROUP", B, "40")));
        assertNumber("600", parts.get(0).amount());
        assertNumber("400", parts.get(1).amount());
        assertNumber("1000", sum(parts));
    }

    @Test
    @DisplayName("rounding never loses or invents a cent: leftover cents go to the largest fractional remainders")
    void roundingRemainder() {
        List<Part> thirds = AgCostAllocation.split(bd("100.00"), List.of(share("CROP_CYCLE", A, "33.33"), share("GROUP", B, "33.33"), share("ANIMAL", C, "33.34")));
        assertNumber("100.00", sum(thirds));
        assertNumber("33.34", thirds.get(2).amount());
        assertNumber("33.33", thirds.get(0).amount());

        // 10.00 split 3 ways equally cannot be exact to the cent: 3.33, 3.33, 3.34
        List<Part> equal = AgCostAllocation.split(bd("10.00"), List.of(share("CROP_CYCLE", A, "33.333"), share("GROUP", B, "33.333"), share("ANIMAL", C, "33.334")));
        assertNumber("10.00", sum(equal));
    }

    @Test
    @DisplayName("amounts are reported to the cent and keep their order")
    void scaleAndOrder() {
        List<Part> parts = AgCostAllocation.split(bd("99.999"), List.of(share("GROUP", B, "25"), share("CROP_CYCLE", A, "75")));
        assertEquals(List.of(B, A), parts.stream().map(Part::targetId).toList());
        for (Part p : parts) assertEquals(2, p.amount().scale());
        assertNumber("100.00", sum(parts));                              // 99.999 is R100.00 to the cent
    }

    @Test
    @DisplayName("when shares tie, the leftover cent goes to the earlier share")
    void tieForLargest() {
        List<Part> parts = AgCostAllocation.split(bd("0.01"), List.of(share("GROUP", B, "50"), share("CROP_CYCLE", A, "50")));
        assertNumber("0.01", sum(parts));
        assertNumber("0.01", parts.get(0).amount());
        assertNumber("0", parts.get(1).amount());
    }

    @Test
    @DisplayName("percentages must total 100, within a half-hundredth")
    void percentagesMustTotal100() {
        assertThrows(IllegalArgumentException.class, () -> AgCostAllocation.split(bd("100"), List.of(share("CROP_CYCLE", A, "60"), share("GROUP", B, "30"))));
        assertThrows(IllegalArgumentException.class, () -> AgCostAllocation.split(bd("100"), List.of(share("CROP_CYCLE", A, "60"), share("GROUP", B, "50"))));
        assertThrows(IllegalArgumentException.class, () -> AgCostAllocation.split(bd("100"), List.of(share("CROP_CYCLE", A, "33.33"), share("GROUP", B, "33.33"), share("ANIMAL", C, "33.33"))));
        assertDoesNotThrow(() -> AgCostAllocation.split(bd("100"), List.of(share("CROP_CYCLE", A, "99.996"), share("GROUP", B, "0.001"))));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> AgCostAllocation.split(bd("100"), List.of(share("CROP_CYCLE", A, "60"), share("GROUP", B, "30"))));
        assertTrue(ex.getMessage().contains("total 100") && ex.getMessage().contains("90"));
    }

    @Test
    @DisplayName("each percentage must be above 0 and at most 100")
    void eachPercentageBounded() {
        assertThrows(IllegalArgumentException.class, () -> AgCostAllocation.split(bd("100"), List.of(share("CROP_CYCLE", A, "0"), share("GROUP", B, "100"))));
        assertThrows(IllegalArgumentException.class, () -> AgCostAllocation.split(bd("100"), List.of(share("CROP_CYCLE", A, "-10"), share("GROUP", B, "110"))));
        assertThrows(IllegalArgumentException.class, () -> AgCostAllocation.split(bd("100"), List.of(share("CROP_CYCLE", A, "101"))));
        assertThrows(IllegalArgumentException.class, () -> AgCostAllocation.split(bd("100"), List.of(new Share("CROP_CYCLE", A, null))));
    }

    @Test
    @DisplayName("the amount must be positive and there must be at least one target")
    void amountAndTargets() {
        assertThrows(IllegalArgumentException.class, () -> AgCostAllocation.split(BigDecimal.ZERO, List.of(share("CROP_CYCLE", A, "100"))));
        assertThrows(IllegalArgumentException.class, () -> AgCostAllocation.split(bd("-5"), List.of(share("CROP_CYCLE", A, "100"))));
        assertThrows(IllegalArgumentException.class, () -> AgCostAllocation.split(null, List.of(share("CROP_CYCLE", A, "100"))));
        assertThrows(IllegalArgumentException.class, () -> AgCostAllocation.split(bd("100"), List.of()));
        assertThrows(IllegalArgumentException.class, () -> AgCostAllocation.split(bd("100"), null));
    }

    @Test
    @DisplayName("only known target types are accepted, each with an id, and a target cannot appear twice")
    void targetsAreValidated() {
        assertThrows(IllegalArgumentException.class, () -> AgCostAllocation.split(bd("100"), List.of(share("TRACTOR", A, "100"))));
        assertThrows(IllegalArgumentException.class, () -> AgCostAllocation.split(bd("100"), List.of(new Share(null, A, bd("100")))));
        assertThrows(IllegalArgumentException.class, () -> AgCostAllocation.split(bd("100"), List.of(new Share("GROUP", null, bd("100")))));
        assertThrows(IllegalArgumentException.class, () -> AgCostAllocation.split(bd("100"), List.of(share("GROUP", A, "50"), share("GROUP", A, "50"))));
        assertDoesNotThrow(() -> AgCostAllocation.split(bd("100"), List.of(share("GROUP", A, "50"), share("CROP_CYCLE", A, "50"))));   // same id, different kind of target
        for (String t : List.of("CROP_CYCLE", "GROUP", "ANIMAL", "ENTERPRISE")) assertDoesNotThrow(() -> AgCostAllocation.split(bd("1"), List.of(share(t, A, "100"))));
    }

    @Test
    @DisplayName("a share's exact value decides who gets a leftover cent, not its position or size alone")
    void largestRemainderDecides() {
        // R10.00 over 10 / 20 / 70 is exact. R0.10 over 33 / 33 / 34: floors are 3, 3, 3 cents; the leftover cent goes to the 34% share (fraction .4 beats .3)
        List<Part> parts = AgCostAllocation.split(bd("0.10"), List.of(share("CROP_CYCLE", A, "33"), share("GROUP", B, "33"), share("ANIMAL", C, "34")));
        assertNumber("0.03", parts.get(0).amount());
        assertNumber("0.03", parts.get(1).amount());
        assertNumber("0.04", parts.get(2).amount());
        assertNumber("0.10", sum(parts));
    }

    @Test
    @DisplayName("a percentage total just inside the tolerance cannot create or lose money, even on a large amount")
    void toleranceDoesNotMoveMoney() {
        for (String total : List.of("99.996", "100.004")) {
            BigDecimal second = new BigDecimal(total).subtract(bd("50"));
            List<Part> parts = AgCostAllocation.split(bd("1000000.00"), List.of(share("CROP_CYCLE", A, "50"), share("GROUP", B, second.toPlainString())));
            assertNumber("1000000.00", sum(parts));
        }
    }

    @Test
    @DisplayName("many small shares still add up exactly")
    void manyShares() {
        List<Share> shares = new java.util.ArrayList<>();
        for (int i = 0; i < 7; i++) shares.add(share("CROP_CYCLE", UUID.randomUUID(), i < 6 ? "14.28" : "14.32"));
        List<Part> parts = AgCostAllocation.split(bd("1234567.89"), shares);
        assertNumber("1234567.89", sum(parts));
        for (Part p : parts) assertTrue(p.amount().signum() > 0);
    }
}
