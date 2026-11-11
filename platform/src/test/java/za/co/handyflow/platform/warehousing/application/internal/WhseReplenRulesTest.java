package za.co.handyflow.platform.warehousing.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.warehousing.application.internal.WhseReplenRules.Source;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WhseReplenRulesTest {

    private static BigDecimal bd(String v) { return new BigDecimal(v); }
    private static Source src(String avail, LocalDate expiry, Integer seq) { return new Source(UUID.randomUUID(), expiry == null ? null : UUID.randomUUID(), expiry, seq, bd(avail)); }

    @Test @DisplayName("levels: the maximum must be above the minimum, nothing negative")
    void levels() {
        assertDoesNotThrow(() -> WhseReplenRules.checkLevels(bd("0"), bd("5")));
        assertThrows(IllegalArgumentException.class, () -> WhseReplenRules.checkLevels(bd("-1"), bd("5")));
        assertThrows(IllegalArgumentException.class, () -> WhseReplenRules.checkLevels(bd("5"), bd("5")));
        assertThrows(IllegalArgumentException.class, () -> WhseReplenRules.checkLevels(bd("1"), null));
        assertThrows(IllegalArgumentException.class, () -> WhseReplenRules.checkLevels(bd("1"), bd("2.1234")));
    }

    @Test @DisplayName("a face at or above its minimum is left alone")
    void notBelow() {
        assertTrue(WhseReplenRules.plan(bd("10"), bd("0"), bd("10"), bd("30"), List.of(src("100", null, 1))).moves().isEmpty());
    }

    @Test @DisplayName("stock already on its way counts towards the minimum and the maximum")
    void inboundCounts() {
        assertTrue(WhseReplenRules.plan(bd("4"), bd("10"), bd("10"), bd("30"), List.of(src("100", null, 1))).moves().isEmpty());
        WhseReplenRules.Plan p = WhseReplenRules.plan(bd("4"), bd("6"), bd("20"), bd("30"), List.of(src("100", null, 1)));
        assertEquals(0, bd("20").compareTo(p.planned())); // 30 - 4 - 6
    }

    @Test @DisplayName("fills to the maximum from the biggest positions first")
    void fillsToMax() {
        Source small = src("5", null, 1), big = src("40", null, 2);
        WhseReplenRules.Plan p = WhseReplenRules.plan(bd("2"), bd("0"), bd("10"), bd("30"), List.of(small, big));
        assertEquals(1, p.moves().size());
        assertSame(big, p.moves().get(0).source());
        assertEquals(0, bd("28").compareTo(p.moves().get(0).qty()));
        assertEquals(0, p.uncovered().signum());
    }

    @Test @DisplayName("lots with the earliest expiry are moved first")
    void firstExpiryFirst() {
        Source late = src("50", LocalDate.of(2027, 6, 1), 1), early = src("6", LocalDate.of(2027, 1, 1), 2);
        WhseReplenRules.Plan p = WhseReplenRules.plan(bd("0"), bd("0"), bd("10"), bd("20"), List.of(late, early));
        assertSame(early, p.moves().get(0).source());
        assertEquals(0, bd("6").compareTo(p.moves().get(0).qty()));
        assertSame(late, p.moves().get(1).source());
        assertEquals(0, bd("14").compareTo(p.moves().get(1).qty()));
    }

    @Test @DisplayName("what the reserve cannot supply is reported")
    void uncovered() {
        WhseReplenRules.Plan p = WhseReplenRules.plan(bd("0"), bd("0"), bd("10"), bd("20"), List.of(src("8", null, 1), src("0", null, 2)));
        assertEquals(0, bd("8").compareTo(p.planned()));
        assertEquals(0, bd("12").compareTo(p.uncovered()));
    }

    @Test @DisplayName("status tells the floor what is going on")
    void status() {
        assertEquals("OK", WhseReplenRules.status(bd("20"), bd("0"), bd("10"), BigDecimal.ZERO));
        assertEquals("FILLING", WhseReplenRules.status(bd("20"), bd("5"), bd("10"), BigDecimal.ZERO));
        assertEquals("BELOW_MIN", WhseReplenRules.status(bd("2"), bd("0"), bd("10"), BigDecimal.ZERO));
        assertEquals("NO_RESERVE", WhseReplenRules.status(bd("2"), bd("0"), bd("10"), BigDecimal.ONE));
    }
}
