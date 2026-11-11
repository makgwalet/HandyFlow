package za.co.handyflow.platform.warehousing.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class WhseWarehouseRulesTest {

    @Test @DisplayName("a code is upper-cased and trimmed; spaces, symbols, blanks and long codes are refused")
    void codes() {
        assertEquals("CPT", WhseWarehouseRules.code(" cpt "));
        assertEquals("JHB-2", WhseWarehouseRules.code("jhb-2"));
        assertEquals("A_1", WhseWarehouseRules.code("a_1"));
        for (String bad : new String[]{"", "  ", null, "-A", "A B", "A/B", "A".repeat(21), "ÄB"}) {
            assertThrows(IllegalArgumentException.class, () -> WhseWarehouseRules.code(bad), String.valueOf(bad));
        }
        assertEquals("A".repeat(20), WhseWarehouseRules.code("a".repeat(20)));
    }

    @Test @DisplayName("a name is needed and at most 120 characters")
    void names() {
        assertEquals("Cape Town DC", WhseWarehouseRules.name("  Cape Town DC "));
        assertThrows(IllegalArgumentException.class, () -> WhseWarehouseRules.name(" "));
        assertThrows(IllegalArgumentException.class, () -> WhseWarehouseRules.name(null));
        assertThrows(IllegalArgumentException.class, () -> WhseWarehouseRules.name("x".repeat(121)));
    }

    @Test @DisplayName("the default warehouse and one holding stock cannot be switched off")
    void deactivate() {
        assertThrows(IllegalStateException.class, () -> WhseWarehouseRules.checkDeactivate(true, BigDecimal.ZERO));
        assertThrows(IllegalStateException.class, () -> WhseWarehouseRules.checkDeactivate(false, new BigDecimal("0.001")));
        assertDoesNotThrow(() -> WhseWarehouseRules.checkDeactivate(false, BigDecimal.ZERO));
        assertDoesNotThrow(() -> WhseWarehouseRules.checkDeactivate(false, null));
    }
}
