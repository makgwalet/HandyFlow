package za.co.handyflow.platform.billing.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Contracting staff endpoints need the contracting module, with or without a trailing slash; public signing links do not. */
class ContractingModuleEntitlementTest {

    @Test
    void contractListAndDashboardNeedTheContractingModule() {
        assertEquals("contracting", ModuleEntitlementPaths.moduleFor("/api/v1/contracts").orElseThrow());
        assertEquals("contracting", ModuleEntitlementPaths.moduleFor("/api/v1/contracts/").orElseThrow());
        assertEquals("contracting", ModuleEntitlementPaths.moduleFor("/api/v1/contracts/dashboard").orElseThrow());
        assertEquals("contracting", ModuleEntitlementPaths.moduleFor("/api/v1/contracts/templates/abc/publish").orElseThrow());
        assertEquals("contracting", ModuleEntitlementPaths.moduleFor("/api/v1/contracts/abc/sharing/log").orElseThrow());
    }

    @Test
    void publicSigningLinksAreNotGatedByTheModule() {
        assertTrue(ModuleEntitlementPaths.moduleFor("/api/v1/sign/some-token/contract").isEmpty());
    }

    @Test
    void similarlyNamedPathsAreNotCaught() {
        assertTrue(ModuleEntitlementPaths.moduleFor("/api/v1/contractsx").isEmpty());
    }
}
