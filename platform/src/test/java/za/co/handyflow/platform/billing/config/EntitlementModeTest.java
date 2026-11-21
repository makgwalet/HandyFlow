package za.co.handyflow.platform.billing.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntitlementModeTest {

    @Test
    void reportOnlyByDefault() {
        EntitlementMode m = new EntitlementMode(false, "");
        assertFalse(m.enforces("security"));
        assertFalse(m.enforces("hr"));
    }

    @Test
    void globalFlagEnforcesEverySellableModule() {
        EntitlementMode m = new EntitlementMode(true, null);
        assertTrue(m.enforces("security"));
        assertTrue(m.enforces("hr"));
    }

    @Test
    void oneModuleCanBeEnforcedAlone() {
        EntitlementMode m = new EntitlementMode(false, " Security , ,pos");
        assertTrue(m.enforces("security"));
        assertTrue(m.enforces("pos"));
        assertFalse(m.enforces("hr"));
    }

    @Test
    void modulesWithNoCatalogueRowAreNeverEnforced() {
        assertFalse(new EntitlementMode(true, "projects").enforces("projects"));
        assertFalse(new EntitlementMode(false, "ap").enforces("ap"));
    }

    @Test
    void nullKeyIsNotEnforced() {
        assertFalse(new EntitlementMode(true, "").enforces(null));
    }
}
