package za.co.handyflow.platform.warehousing.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.warehousing.application.internal.WhsePackageRules.Enforcement;
import za.co.handyflow.platform.warehousing.application.internal.WhsePackageRules.Limit;

import static org.junit.jupiter.api.Assertions.*;

class WhsePackageRulesTest {

    @Test @DisplayName("null limit is unlimited")
    void unlimited() {
        assertFalse(WhsePackageRules.wouldExceed(1_000_000, null));
        assertFalse(WhsePackageRules.isOver(1_000_000, null));
    }

    @Test @DisplayName("adding the record that reaches the limit is allowed, the next one is not")
    void boundary() {
        assertFalse(WhsePackageRules.wouldExceed(2, 3));
        assertTrue(WhsePackageRules.wouldExceed(3, 3));
        assertFalse(WhsePackageRules.isOver(3, 3));
        assertTrue(WhsePackageRules.isOver(4, 3));
    }

    @Test @DisplayName("a limit of zero refuses the first one")
    void zero() {
        assertTrue(WhsePackageRules.wouldExceed(0, 0));
    }

    @Test @DisplayName("only ENFORCE refuses, and the message names the package, limit and usage")
    void enforcement() {
        assertDoesNotThrow(() -> WhsePackageRules.checkAdd(Limit.WAREHOUSES, 1, 1, Enforcement.OFF, "Starter"));
        assertDoesNotThrow(() -> WhsePackageRules.checkAdd(Limit.WAREHOUSES, 1, 1, Enforcement.REPORT, "Starter"));
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> WhsePackageRules.checkAdd(Limit.WAREHOUSES, 1, 1, Enforcement.ENFORCE, "Starter"));
        assertTrue(e.getMessage().contains("Starter") && e.getMessage().contains("1 warehouses") && e.getMessage().contains("larger package"));
        assertDoesNotThrow(() -> WhsePackageRules.checkAdd(Limit.WAREHOUSES, 0, 1, Enforcement.ENFORCE, "Starter"));
    }

    @Test @DisplayName("enforcement parses case-insensitively and rejects the rest")
    void parse() {
        assertEquals(Enforcement.ENFORCE, WhsePackageRules.parseEnforcement(" enforce "));
        assertThrows(IllegalArgumentException.class, () -> WhsePackageRules.parseEnforcement("maybe"));
        assertThrows(IllegalArgumentException.class, () -> WhsePackageRules.parseEnforcement(null));
    }
}
