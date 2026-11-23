package za.co.handyflow.platform.agriculture.domain.rules;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AgMarkRulesTest {

    @Test void trimsAndTurnsBlankIntoNoMark() {
        assertEquals("ZK 4821", AgMarkRules.normalise("  ZK 4821 ", "Brand mark"));
        assertNull(AgMarkRules.normalise("   ", "Brand mark"));
        assertNull(AgMarkRules.normalise("", "Brand mark"));
        assertNull(AgMarkRules.normalise(null, "Brand mark"));
    }

    @Test void sixtyCharactersIsTheLimit() {
        assertEquals(60, AgMarkRules.normalise("a".repeat(60), "Ear mark").length());
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> AgMarkRules.normalise("a".repeat(61), "Ear mark"));
        assertTrue(e.getMessage().startsWith("Ear mark"), e.getMessage());
    }

    @Test void unprintableCharactersAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> AgMarkRules.normalise("ab\ncd", "Brand mark"));
        assertThrows(IllegalArgumentException.class, () -> AgMarkRules.normalise("ab\u0000cd", "Brand mark"));
    }
}
