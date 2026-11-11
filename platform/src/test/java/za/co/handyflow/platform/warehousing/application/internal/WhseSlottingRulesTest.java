package za.co.handyflow.platform.warehousing.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.warehousing.application.internal.WhseSlottingRules.Empty;
import za.co.handyflow.platform.warehousing.application.internal.WhseSlottingRules.Placed;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WhseSlottingRulesTest {

    private static BigDecimal bd(String v) { return new BigDecimal(v); }
    private static Placed placed(UUID item, int seq, String avail, String picked, String abc) {
        return new Placed(UUID.randomUUID(), item, null, UUID.randomUUID(), "P" + seq, seq, bd(avail), bd(picked), abc);
    }
    private static Empty empty(int seq, String cap) { return new Empty(UUID.randomUUID(), "E" + seq, seq, cap == null ? null : bd(cap)); }

    @Test @DisplayName("items are classed by share of units picked: 80% A, next 15% B, the rest and the unpicked C")
    void classify() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID(), d = UUID.randomUUID(), none = UUID.randomUUID();
        Map<UUID, BigDecimal> picked = new LinkedHashMap<>();
        picked.put(a, bd("800")); picked.put(b, bd("150")); picked.put(c, bd("40")); picked.put(d, bd("10"));
        Map<UUID, String> abc = WhseSlottingRules.classify(picked, Set.of(a, b, c, d, none));
        assertEquals("A", abc.get(a));
        assertEquals("B", abc.get(b));
        assertEquals("C", abc.get(c));
        assertEquals("C", abc.get(d));
        assertEquals("C", abc.get(none));
    }

    @Test @DisplayName("the first item is always A, and with no picks everything is C")
    void edges() {
        UUID only = UUID.randomUUID(), other = UUID.randomUUID();
        assertEquals("A", WhseSlottingRules.classify(Map.of(only, bd("5")), Set.of(only, other)).get(only));
        Map<UUID, String> none = WhseSlottingRules.classify(Map.of(), Set.of(only));
        assertEquals("C", none.get(only));
        assertEquals("C", WhseSlottingRules.classify(Map.of(only, BigDecimal.ZERO), Set.of(only)).get(only));
    }

    @Test @DisplayName("a busy item moves to the nearest empty location that is better by enough")
    void movesForward() {
        UUID item = UUID.randomUUID();
        Empty near = empty(2, null), nearer = empty(1, null), far = empty(40, null);
        List<WhseSlottingRules.Move> m = WhseSlottingRules.plan(List.of(placed(item, 30, "10", "500", "A")), List.of(far, near, nearer), 5);
        assertEquals(1, m.size());
        assertSame(nearer, m.get(0).to());
        assertEquals(29, m.get(0).improvement());
    }

    @Test @DisplayName("no move when the gain is too small, or the item is already at the front")
    void notWorthIt() {
        UUID item = UUID.randomUUID();
        assertTrue(WhseSlottingRules.plan(List.of(placed(item, 8, "10", "500", "A")), List.of(empty(5, null)), 5).isEmpty());
        assertTrue(WhseSlottingRules.plan(List.of(placed(item, 3, "10", "500", "A")), List.of(empty(10, null)), 1).isEmpty());
    }

    @Test @DisplayName("C items and items nobody picked are never moved forward")
    void onlyBusyItems() {
        assertTrue(WhseSlottingRules.plan(List.of(placed(UUID.randomUUID(), 50, "10", "3", "C"), placed(UUID.randomUUID(), 50, "10", "0", "B")), List.of(empty(1, null)), 5).isEmpty());
    }

    @Test @DisplayName("the busiest item gets the best spot, and each empty location is offered once")
    void eachSpotOnce() {
        Placed busy = placed(UUID.randomUUID(), 50, "10", "900", "A"), less = placed(UUID.randomUUID(), 60, "10", "300", "B");
        Empty best = empty(1, null), second = empty(2, null);
        List<WhseSlottingRules.Move> m = WhseSlottingRules.plan(List.of(less, busy), List.of(second, best), 5);
        assertEquals(2, m.size());
        assertSame(busy, m.get(0).from());
        assertSame(best, m.get(0).to());
        assertSame(less, m.get(1).from());
        assertSame(second, m.get(1).to());
        assertEquals(1, WhseSlottingRules.plan(List.of(less, busy), List.of(best), 5).size());
    }

    @Test @DisplayName("a location too small for the stock is skipped, and one off the pick walk is not used")
    void capacityAndWalk() {
        Placed p = placed(UUID.randomUUID(), 50, "20", "500", "A");
        Empty small = empty(1, "5"), ok = empty(2, "20");
        List<WhseSlottingRules.Move> m = WhseSlottingRules.plan(List.of(p), List.of(small, ok, new Empty(UUID.randomUUID(), "OFF", null, null)), 5);
        assertSame(ok, m.get(0).to());
    }

    @Test @DisplayName("an item off the walk (no sequence) counts as farthest, so any walk position improves it")
    void offWalk() {
        Placed p = new Placed(UUID.randomUUID(), UUID.randomUUID(), null, UUID.randomUUID(), "X", null, bd("5"), bd("100"), "A");
        assertEquals(1, WhseSlottingRules.plan(List.of(p), List.of(empty(10, null)), 5).size());
    }
}
