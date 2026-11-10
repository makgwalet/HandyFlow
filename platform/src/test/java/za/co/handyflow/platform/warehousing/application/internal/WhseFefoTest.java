package za.co.handyflow.platform.warehousing.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.warehousing.application.internal.WhseFefo.Candidate;
import za.co.handyflow.platform.warehousing.application.internal.WhseFefo.Pick;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WhseFefoTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);

    private static Candidate c(String lot, LocalDate expiry, boolean blocked, String qty) {
        return new Candidate(UUID.randomUUID(), UUID.randomUUID(), lot, UUID.randomUUID(), expiry, blocked, new BigDecimal(qty));
    }

    private static BigDecimal bd(String v) { return new BigDecimal(v); }

    @Test @DisplayName("takes the earliest expiry first and spills into the next lot")
    void earliestFirst() {
        List<Pick> p = WhseFefo.plan(List.of(c("B", TODAY.plusDays(90), false, "10"), c("A", TODAY.plusDays(30), false, "6")), bd("8"), TODAY, null);
        assertEquals(2, p.size());
        assertEquals("A", p.get(0).source().lotNumber());
        assertEquals(0, bd("6").compareTo(p.get(0).qty()));
        assertEquals("B", p.get(1).source().lotNumber());
        assertEquals(0, bd("2").compareTo(p.get(1).qty()));
    }

    @Test @DisplayName("lots without an expiry date come last")
    void nullsLast() {
        List<Pick> p = WhseFefo.plan(List.of(c("N", null, false, "5"), c("D", TODAY.plusDays(400), false, "5")), bd("7"), TODAY, null);
        assertEquals("D", p.get(0).source().lotNumber());
        assertEquals("N", p.get(1).source().lotNumber());
    }

    @Test @DisplayName("blocked and expired lots are skipped")
    void skipsBlockedAndExpired() {
        List<Pick> p = WhseFefo.plan(List.of(c("BLK", TODAY.plusDays(5), true, "50"), c("OLD", TODAY.minusDays(1), false, "50"), c("OK", TODAY.plusDays(60), false, "50")), bd("10"), TODAY, null);
        assertEquals(1, p.size());
        assertEquals("OK", p.get(0).source().lotNumber());
    }

    @Test @DisplayName("lots with too little shelf life are skipped")
    void minShelfLife() {
        List<Pick> p = WhseFefo.plan(List.of(c("SHORT", TODAY.plusDays(10), false, "50"), c("LONG", TODAY.plusDays(100), false, "50")), bd("5"), TODAY, 30);
        assertEquals("LONG", p.get(0).source().lotNumber());
    }

    @Test @DisplayName("a shortfall says what was held back")
    void shortfallMessage() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> WhseFefo.plan(List.of(c("BLK", TODAY.plusDays(5), true, "4"), c("OLD", TODAY.minusDays(1), false, "3"), c("OK", TODAY.plusDays(60), false, "2")), bd("10"), TODAY, null));
        assertTrue(e.getMessage().contains("10 needed, 2 available"), e.getMessage());
        assertTrue(e.getMessage().contains("4 is in blocked lots"), e.getMessage());
        assertTrue(e.getMessage().contains("3 is expired"), e.getMessage());
    }

    @Test @DisplayName("the plan is repeatable for equal expiry dates")
    void deterministic() {
        Candidate a = c("A", TODAY.plusDays(30), false, "5"), b = c("B", TODAY.plusDays(30), false, "5");
        assertEquals(WhseFefo.plan(List.of(b, a), bd("5"), TODAY, null).get(0).source().lotNumber(),
                WhseFefo.plan(List.of(a, b), bd("5"), TODAY, null).get(0).source().lotNumber());
    }

    @Test @DisplayName("a zero quantity is refused")
    void zeroQty() {
        assertThrows(IllegalArgumentException.class, () -> WhseFefo.plan(List.of(), BigDecimal.ZERO, TODAY, null));
    }

    @Test @DisplayName("planUpTo takes what is sellable and skips blocked lots without failing")
    void upTo() {
        List<Pick> p = WhseFefo.planUpTo(List.of(c("A", TODAY.plusDays(30), false, "4"), c("BLK", TODAY.plusDays(10), true, "50")), bd("10"), TODAY, null);
        assertEquals(1, p.size());
        assertEquals("A", p.get(0).source().lotNumber());
        assertEquals(0, bd("4").compareTo(p.get(0).qty()));
        assertTrue(WhseFefo.planUpTo(List.of(), bd("1"), TODAY, null).isEmpty());
    }
}
