package za.co.handyflow.platform.clinic.application.internal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RoomRulesTest {

    @Test
    void tidiesTheName() {
        assertEquals("Room 2", RoomRules.cleanName("  Room   2 "));
        assertEquals("Procedure room", RoomRules.cleanName("Procedure room"));
    }

    @Test
    void refusesEmptyOrOverLongNames() {
        assertThrows(IllegalArgumentException.class, () -> RoomRules.cleanName(null));
        assertThrows(IllegalArgumentException.class, () -> RoomRules.cleanName("   "));
        assertThrows(IllegalArgumentException.class, () -> RoomRules.cleanName("x".repeat(61)));
        assertEquals(60, RoomRules.cleanName("x".repeat(60)).length());
    }
}
