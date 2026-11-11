package za.co.handyflow.platform.warehousing.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class WhseDockRulesTest {

    private static final Instant T0 = Instant.parse("2026-11-12T08:00:00Z");
    private static Instant at(int minutes) { return T0.plus(Duration.ofMinutes(minutes)); }

    @Test @DisplayName("direction is inbound or outbound, any case")
    void direction() {
        assertEquals("INBOUND", WhseDockRules.direction(" inbound "));
        assertEquals("OUTBOUND", WhseDockRules.direction("OUTBOUND"));
        assertThrows(IllegalArgumentException.class, () -> WhseDockRules.direction("sideways"));
        assertThrows(IllegalArgumentException.class, () -> WhseDockRules.direction(null));
    }

    @Test @DisplayName("a slot must be 5 minutes to 12 hours and end after it starts")
    void slot() {
        assertDoesNotThrow(() -> WhseDockRules.checkSlot(Instant.now().plusSeconds(3600), Instant.now().plusSeconds(7200)));
        assertThrows(IllegalArgumentException.class, () -> WhseDockRules.checkSlot(null, at(30)));
        assertThrows(IllegalArgumentException.class, () -> WhseDockRules.checkSlot(at(30), at(30)));
        assertThrows(IllegalArgumentException.class, () -> WhseDockRules.checkSlot(at(30), at(10)));
        assertThrows(IllegalArgumentException.class, () -> WhseDockRules.checkSlot(at(0), at(3)));
        assertThrows(IllegalArgumentException.class, () -> WhseDockRules.checkSlot(at(0), at(12 * 60 + 1)));
        assertThrows(IllegalArgumentException.class, () -> WhseDockRules.checkSlot(Instant.now().plus(Duration.ofDays(400)), Instant.now().plus(Duration.ofDays(400)).plusSeconds(3600)));
    }

    @Test @DisplayName("slots clash when they share time, not when they touch")
    void overlap() {
        assertTrue(WhseDockRules.overlaps(at(0), at(60), at(30), at(90)));
        assertTrue(WhseDockRules.overlaps(at(0), at(60), at(10), at(20)));
        assertTrue(WhseDockRules.overlaps(at(30), at(90), at(0), at(60)));
        assertFalse(WhseDockRules.overlaps(at(0), at(60), at(60), at(120)));
        assertFalse(WhseDockRules.overlaps(at(60), at(120), at(0), at(60)));
        assertFalse(WhseDockRules.overlaps(at(0), at(30), at(90), at(120)));
    }

    @Test @DisplayName("the allowed moves between statuses")
    void transitions() {
        assertDoesNotThrow(() -> WhseDockRules.checkTransition("SCHEDULED", "ARRIVED"));
        assertDoesNotThrow(() -> WhseDockRules.checkTransition("SCHEDULED", "NO_SHOW"));
        assertDoesNotThrow(() -> WhseDockRules.checkTransition("ARRIVED", "AT_DOCK"));
        assertDoesNotThrow(() -> WhseDockRules.checkTransition("ARRIVED", "CANCELLED"));
        assertDoesNotThrow(() -> WhseDockRules.checkTransition("AT_DOCK", "COMPLETED"));
        assertThrows(IllegalStateException.class, () -> WhseDockRules.checkTransition("SCHEDULED", "AT_DOCK"));
        assertThrows(IllegalStateException.class, () -> WhseDockRules.checkTransition("SCHEDULED", "COMPLETED"));
        assertThrows(IllegalStateException.class, () -> WhseDockRules.checkTransition("AT_DOCK", "CANCELLED"));
        assertThrows(IllegalStateException.class, () -> WhseDockRules.checkTransition("ARRIVED", "NO_SHOW"));
        for (String done : new String[]{"COMPLETED", "CANCELLED", "NO_SHOW"}) {
            assertThrows(IllegalStateException.class, () -> WhseDockRules.checkTransition(done, "ARRIVED"));
        }
    }

    @Test @DisplayName("a no-show needs the slot to have started")
    void noShow() {
        assertThrows(IllegalStateException.class, () -> WhseDockRules.checkNoShow(at(60), at(30)));
        assertDoesNotThrow(() -> WhseDockRules.checkNoShow(at(60), at(60)));
        assertDoesNotThrow(() -> WhseDockRules.checkNoShow(at(60), at(90)));
    }

    @Test @DisplayName("lateness counts minutes after the slot start only")
    void late() {
        assertEquals(0, WhseDockRules.lateMinutes(at(60), at(30)));
        assertEquals(0, WhseDockRules.lateMinutes(at(60), null));
        assertEquals(25, WhseDockRules.lateMinutes(at(60), at(85)));
    }
}
