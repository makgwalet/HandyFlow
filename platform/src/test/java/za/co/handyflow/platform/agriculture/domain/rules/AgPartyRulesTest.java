package za.co.handyflow.platform.agriculture.domain.rules;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AgPartyRulesTest {

    @Test void trimsAndTurnsBlankIntoNull() {
        assertEquals("J Smith", AgPartyRules.clean("  J Smith ", "Buyer name", 200));
        assertNull(AgPartyRules.clean("   ", "Buyer name", 200));
        assertNull(AgPartyRules.clean(null, "Buyer name", 200));
    }

    @Test void lengthLimitIsInclusive() {
        assertEquals(10, AgPartyRules.clean("a".repeat(10), "x", 10).length());
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> AgPartyRules.clean("a".repeat(11), "Buyer name", 10));
        assertTrue(e.getMessage().startsWith("Buyer name"), e.getMessage());
    }

    @Test void lineBreaksAndControlCharactersAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> AgPartyRules.clean("a\nb", "x", 50));
        assertThrows(IllegalArgumentException.class, () -> AgPartyRules.clean("a\tb", "x", 50));
        assertThrows(IllegalArgumentException.class, () -> AgPartyRules.clean("a\u0000b", "x", 50));
    }

    @Test void vehicleRegistrationsAreUpperCaseAndShort() {
        assertEquals("CA 123-456", AgPartyRules.vehicle(" ca 123-456 "));
        assertNull(AgPartyRules.vehicle(" "));
        assertThrows(IllegalArgumentException.class, () -> AgPartyRules.vehicle("x".repeat(31)));
    }
}
