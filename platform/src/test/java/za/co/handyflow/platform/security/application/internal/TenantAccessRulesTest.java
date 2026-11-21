package za.co.handyflow.platform.security.application.internal;

import static org.junit.jupiter.api.Assertions.*;
import static za.co.handyflow.platform.security.application.internal.TenantAccessRules.Access.*;

import org.junit.jupiter.api.Test;

class TenantAccessRulesTest {
    private static final String ID = "123e4567-e89b-12d3-a456-426614174000";

    @Test void statusMapping() {
        assertEquals(FULL, TenantAccessRules.accessFor(null));
        assertEquals(FULL, TenantAccessRules.accessFor("ACTIVE"));
        assertEquals(FULL, TenantAccessRules.accessFor("PILOT"));
        assertEquals(FULL, TenantAccessRules.accessFor("PAST_DUE"));
        assertEquals(FULL, TenantAccessRules.accessFor("something-new"));
        assertEquals(RESTRICTED, TenantAccessRules.accessFor("suspended"));
        assertEquals(BLOCKED, TenantAccessRules.accessFor("CANCELLED"));
    }
    @Test void fullAllowsAndBlockedDenies() {
        assertTrue(TenantAccessRules.permits(FULL, "POST", "/api/v1/guard/sessions/open"));
        assertFalse(TenantAccessRules.permits(BLOCKED, "POST", "/api/v1/guard/duress"));
    }
    @Test void restrictedKeepsSafetyAndCloseOut() {
        assertTrue(TenantAccessRules.permits(RESTRICTED, "POST", "/api/v1/guard/duress"));
        assertTrue(TenantAccessRules.permits(RESTRICTED, "POST", "/api/v1/guard/sessions/" + ID + "/close"));
        assertTrue(TenantAccessRules.permits(RESTRICTED, "POST", "/api/v1/guard/sessions/resources/" + ID + "/return"));
        assertTrue(TenantAccessRules.permits(RESTRICTED, "POST", "/api/v1/guard/checkpoints/scan"));
        assertTrue(TenantAccessRules.permits(RESTRICTED, "POST", "/api/v1/guard/incidents/by-ref/INC-1/evidence"));
        assertTrue(TenantAccessRules.permits(RESTRICTED, "GET", "/api/v1/guard/sessions/current"));
        assertTrue(TenantAccessRules.permits(RESTRICTED, "POST", "/api/v1/guard/duress/"));
    }
    @Test void restrictedStopsNewWork() {
        assertFalse(TenantAccessRules.permits(RESTRICTED, "POST", "/api/v1/guard/sessions/open"));
        assertFalse(TenantAccessRules.permits(RESTRICTED, "POST", "/api/v1/guard/sessions/" + ID + "/resources/checkout"));
        assertFalse(TenantAccessRules.permits(RESTRICTED, "POST", "/api/v1/guard/shifts/swaps"));
        assertFalse(TenantAccessRules.permits(RESTRICTED, "GET", "/api/v1/guard/gate/visitors"));
        assertFalse(TenantAccessRules.permits(RESTRICTED, "GET", "/api/v1/guard/duress"));
        assertFalse(TenantAccessRules.permits(RESTRICTED, "POST", "/api/v1/guard/duress/extra"));
    }
}
