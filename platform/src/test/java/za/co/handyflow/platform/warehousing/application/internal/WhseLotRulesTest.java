package za.co.handyflow.platform.warehousing.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class WhseLotRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);

    @Test @DisplayName("on its expiry date a lot is still usable; the day after it is expired")
    void expiryBoundary() {
        assertEquals("OK", WhseLotRules.expiryStatus(TODAY.plusDays(31), TODAY, 30));
        assertEquals("EXPIRING", WhseLotRules.expiryStatus(TODAY.plusDays(30), TODAY, 30));
        assertEquals("EXPIRING", WhseLotRules.expiryStatus(TODAY, TODAY, 30));
        assertEquals("EXPIRED", WhseLotRules.expiryStatus(TODAY.minusDays(1), TODAY, 30));
        assertEquals("NO_EXPIRY", WhseLotRules.expiryStatus(null, TODAY, 30));
    }

    @Test @DisplayName("days to expiry is negative once expired and null without a date")
    void days() {
        assertEquals(5L, WhseLotRules.daysToExpiry(TODAY.plusDays(5), TODAY));
        assertEquals(-2L, WhseLotRules.daysToExpiry(TODAY.minusDays(2), TODAY));
        assertNull(WhseLotRules.daysToExpiry(null, TODAY));
    }

    @Test @DisplayName("sellable needs the lot to be unexpired with at least the minimum shelf life left")
    void sellable() {
        assertTrue(WhseLotRules.sellable(null, TODAY, 90));
        assertTrue(WhseLotRules.sellable(TODAY.plusDays(90), TODAY, 90));
        assertFalse(WhseLotRules.sellable(TODAY.plusDays(89), TODAY, 90));
        assertTrue(WhseLotRules.sellable(TODAY, TODAY, null));
        assertTrue(WhseLotRules.sellable(TODAY, TODAY, 0));
        assertFalse(WhseLotRules.sellable(TODAY.minusDays(1), TODAY, null));
    }

    @Test @DisplayName("an item that is not lot tracked takes no lot data")
    void notTracked() {
        assertNull(WhseLotRules.validateLotInput(false, false, null, null, null));
        assertNull(WhseLotRules.validateLotInput(false, false, "  ", null, null));
        assertThrows(IllegalArgumentException.class, () -> WhseLotRules.validateLotInput(false, false, "L1", null, null));
        assertThrows(IllegalArgumentException.class, () -> WhseLotRules.validateLotInput(false, false, null, TODAY, null));
    }

    @Test @DisplayName("a lot-tracked item needs a lot number, and an expiry date only if it expires")
    void tracked() {
        assertEquals("L1", WhseLotRules.validateLotInput(true, false, " L1 ", null, null));
        assertThrows(IllegalArgumentException.class, () -> WhseLotRules.validateLotInput(true, false, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> WhseLotRules.validateLotInput(true, true, "L1", null, null));
        assertThrows(IllegalArgumentException.class, () -> WhseLotRules.validateLotInput(true, false, "L1", TODAY, null));
        assertEquals("L1", WhseLotRules.validateLotInput(true, true, "L1", TODAY.plusDays(100), TODAY.minusDays(10)));
    }

    @Test @DisplayName("expiry cannot be before manufacture, and the lot number has a length limit")
    void dates() {
        assertThrows(IllegalArgumentException.class, () -> WhseLotRules.validateLotInput(true, true, "L1", TODAY.minusDays(5), TODAY));
        assertThrows(IllegalArgumentException.class, () -> WhseLotRules.validateLotInput(true, false, "x".repeat(61), null, null));
    }
}
