package za.co.handyflow.platform.warehousing.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.warehousing.application.internal.WhseSplitPlan.Source;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WhseSplitPlanTest {

    private static Source s(Integer seq, String avail) { return new Source(UUID.randomUUID(), UUID.randomUUID(), seq, new BigDecimal(avail)); }
    private static BigDecimal bd(String v) { return new BigDecimal(v); }

    @Test @DisplayName("one location that holds it all is used, the earliest in pick order")
    void singleCoveringWins() {
        Source far = s(9, "50"), near = s(2, "20"), small = s(1, "5");
        WhseSplitPlan.Result r = WhseSplitPlan.plan(List.of(far, small, near), bd("15"));
        assertEquals(1, r.pieces().size());
        assertSame(near, r.pieces().get(0).source());
        assertEquals(0, r.unallocated().signum());
    }

    @Test @DisplayName("otherwise the biggest positions go first, so there are few stops")
    void splitsBiggestFirst() {
        Source a = s(1, "4"), b = s(2, "10"), c = s(3, "6");
        WhseSplitPlan.Result r = WhseSplitPlan.plan(List.of(a, b, c), bd("18"));
        assertEquals(3, r.pieces().size());
        assertSame(b, r.pieces().get(0).source());
        assertSame(c, r.pieces().get(1).source());
        assertEquals(0, bd("2").compareTo(r.pieces().get(2).qty()));
        assertEquals(0, r.unallocated().signum());
    }

    @Test @DisplayName("what the stock cannot cover is reported, not hidden")
    void shortage() {
        WhseSplitPlan.Result r = WhseSplitPlan.plan(List.of(s(1, "4"), s(2, "3")), bd("10"));
        assertEquals(0, bd("7").compareTo(r.allocated()));
        assertEquals(0, bd("3").compareTo(r.unallocated()));
    }

    @Test @DisplayName("empty or used-up positions are ignored")
    void ignoresEmpty() {
        WhseSplitPlan.Result r = WhseSplitPlan.plan(List.of(s(1, "0"), s(2, "-1")), bd("3"));
        assertTrue(r.pieces().isEmpty());
        assertEquals(0, bd("3").compareTo(r.unallocated()));
    }

    @Test @DisplayName("positions without a pick sequence come last among equals")
    void nullSequenceLast() {
        Source none = s(null, "5"), seq = s(4, "5");
        assertSame(seq, WhseSplitPlan.plan(List.of(none, seq), bd("5")).pieces().get(0).source());
    }

    @Test @DisplayName("a zero quantity is refused")
    void zero() {
        assertThrows(IllegalArgumentException.class, () -> WhseSplitPlan.plan(List.of(), BigDecimal.ZERO));
    }
}
