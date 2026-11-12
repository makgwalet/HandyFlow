package za.co.handyflow.platform.fuel.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.fuel.application.internal.FuelRouteOptimiser.Result;
import za.co.handyflow.platform.fuel.application.internal.FuelRouteOptimiser.Stop;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FuelRouteOptimiserTest {

    static BigDecimal bd(String s) { return new BigDecimal(s); }
    static final Instant NOON = Instant.parse("2026-11-12T10:00:00Z");

    /** A stop on a west-east line at one latitude: longitude steps of 0.1 degree are about 9 km apart. */
    static Stop at(UUID id, String lng) { return new Stop(id, true, bd("-26.0"), bd(lng), null, null, null); }
    static Stop fixed(UUID id, String lng) { return new Stop(id, false, bd("-26.0"), bd(lng), null, null, null); }
    static Stop timed(UUID id, String lng, int hoursEnd) { return new Stop(id, true, bd("-26.0"), bd(lng), null, null, NOON.plusSeconds(hoursEnd * 3600L)); }
    static Stop noPoint(UUID id, String distKm, Integer hoursEnd) {
        return new Stop(id, true, null, null, distKm == null ? null : bd(distKm), null, hoursEnd == null ? null : NOON.plusSeconds(hoursEnd * 3600L));
    }
    static List<UUID> ids(UUID... u) { return List.of(u); }

    final UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID(), d = UUID.randomUUID();

    @Test @DisplayName("a zig-zag becomes a straight sweep and the saving is reported")
    void zigZagBecomesASweep() {
        // positions along the line: a=28.0, b=28.4, c=28.1, d=28.3 (current order a, b, c, d zig-zags)
        Result r = FuelRouteOptimiser.optimise(List.of(at(a, "28.0"), at(b, "28.4"), at(c, "28.1"), at(d, "28.3")), bd("-26.0"), bd("28.0"));
        assertEquals(FuelRouteOptimiser.STRAIGHT_LINE, r.method());
        assertTrue(r.changed());
        // on a closed loop from the start point a sweep either way is equally short, so accept both directions
        assertTrue(r.order().equals(ids(a, c, d, b)) || r.order().equals(ids(b, d, c, a)), "expected a sweep along the line but got " + r.order());
        assertTrue(r.suggestedKm().compareTo(r.currentKm()) < 0);
        assertEquals(0, bd("80.0").compareTo(r.suggestedKm()));
    }

    @Test @DisplayName("an order that is already shortest is left alone")
    void alreadyShortest() {
        Result r = FuelRouteOptimiser.optimise(List.of(at(a, "28.0"), at(b, "28.1"), at(c, "28.2")), bd("-26.0"), bd("27.9"));
        assertFalse(r.changed());
        assertEquals(ids(a, b, c), r.order());
        assertEquals(r.currentKm(), r.suggestedKm());
    }

    @Test @DisplayName("stops with a delivery window keep their window order even when that costs distance")
    void windowOrderKept() {
        // b's window ends before a's, so b must stay ahead of a although a is nearer the start
        Result r = FuelRouteOptimiser.optimise(List.of(timed(a, "28.1", 9), timed(b, "28.4", 6), at(c, "28.2")), bd("-26.0"), bd("28.0"));
        List<UUID> o = r.order();
        assertTrue(o.indexOf(b) < o.indexOf(a));
    }

    @Test @DisplayName("a stop already on the road or delivered keeps its place; only scheduled stops move")
    void fixedStopsStay() {
        Result r = FuelRouteOptimiser.optimise(List.of(fixed(a, "28.0"), at(b, "28.4"), at(c, "28.1"), at(d, "28.3")), null, null);
        assertEquals(a, r.order().get(0));
        assertEquals(ids(a, c, d, b), r.order());
    }

    @Test @DisplayName("with no start point it measures from the last fixed stop before the movable ones")
    void anchorFromFixedStop() {
        Result r = FuelRouteOptimiser.optimise(List.of(fixed(a, "28.0"), at(b, "28.3"), at(c, "28.1")), null, null);
        assertEquals(ids(a, c, b), r.order());
    }

    @Test @DisplayName("without coordinates it orders by window first, then nearest first by stored distance")
    void fallback() {
        Result r = FuelRouteOptimiser.optimise(List.of(noPoint(a, "80", null), noPoint(b, "20", null), noPoint(c, "50", 5), noPoint(d, "10", 3)), null, null);
        assertEquals(FuelRouteOptimiser.WINDOWS_AND_DISTANCE, r.method());
        assertEquals(ids(d, c, b, a), r.order());
        assertNull(r.currentKm());
        assertNull(r.suggestedKm());
    }

    @Test @DisplayName("one coordinate missing among the movable stops means the fallback is used")
    void oneMissingMeansFallback() {
        Result r = FuelRouteOptimiser.optimise(List.of(at(a, "28.0"), noPoint(b, "5", null)), null, null);
        assertEquals(FuelRouteOptimiser.WINDOWS_AND_DISTANCE, r.method());
    }

    @Test @DisplayName("fewer than two movable stops means nothing to reorder")
    void nothingToReorder() {
        Result one = FuelRouteOptimiser.optimise(List.of(fixed(a, "28.0"), at(b, "28.4")), null, null);
        assertFalse(one.changed());
        Result none = FuelRouteOptimiser.optimise(List.of(fixed(a, "28.0"), fixed(b, "28.4")), null, null);
        assertFalse(none.changed());
        assertEquals(ids(a, b), none.order());
    }

    @Test @DisplayName("the suggestion is never longer than an order that already respects the windows")
    void neverWorse() {
        List<Stop> in = List.of(at(a, "28.0"), at(b, "28.1"), at(c, "28.2"), at(d, "28.3"));
        Result r = FuelRouteOptimiser.optimise(in, bd("-26.0"), bd("28.0"));
        assertTrue(r.suggestedKm().compareTo(r.currentKm()) <= 0);
    }

    @Test @DisplayName("the same input always gives the same answer")
    void deterministic() {
        List<Stop> in = List.of(at(a, "28.0"), at(b, "28.4"), at(c, "28.1"), at(d, "28.3"));
        assertEquals(FuelRouteOptimiser.optimise(in, bd("-26.0"), bd("28.0")).order(), FuelRouteOptimiser.optimise(in, bd("-26.0"), bd("28.0")).order());
    }

    @Test @DisplayName("straight-line distance is about 9.99 km for 0.1 degree of longitude at 26 degrees south")
    void haversineSanity() {
        double km = FuelRouteOptimiser.haversine(-26.0, 28.0, -26.0, 28.1);
        assertEquals(10.0, km, 0.2);
    }
}
