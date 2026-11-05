package za.co.handyflow.platform.billing.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ModuleEntitlementPathsTest {

    @Test
    @DisplayName("staff paths map to their module, including the bare prefix")
    void mapsStaffPaths() {
        assertThat(ModuleEntitlementPaths.moduleFor("/api/v1/hr/employees")).contains("hr");
        assertThat(ModuleEntitlementPaths.moduleFor("/api/v1/hr")).contains("hr");
        assertThat(ModuleEntitlementPaths.moduleFor("/api/v1/guard/gate/open")).contains("security");
        assertThat(ModuleEntitlementPaths.moduleFor("/api/v1/fleet/telematics/positions")).contains("fleet");
        assertThat(ModuleEntitlementPaths.moduleFor("/api/v1/contracts/123/sharing")).contains("contracting");
    }

    @Test
    @DisplayName("signing links, public paths, portals and similarly named prefixes are not covered")
    void leavesPublicPathsAlone() {
        assertThat(ModuleEntitlementPaths.moduleFor("/api/v1/sign/abc")).isEmpty();
        assertThat(ModuleEntitlementPaths.moduleFor("/api/public/projects/portal")).isEmpty();
        assertThat(ModuleEntitlementPaths.moduleFor("/api/v1/hrx/anything")).isEmpty();
        assertThat(ModuleEntitlementPaths.moduleFor("/api/v1/crm/customers")).isEmpty();   // free core
        assertThat(ModuleEntitlementPaths.moduleFor(null)).isEmpty();
    }

    @Test
    @DisplayName("modules added in Phase A map to their catalogue key")
    void mapsPhaseAModules() {
        assertThat(ModuleEntitlementPaths.moduleFor("/api/v1/pos/sales")).contains("pos");
        assertThat(ModuleEntitlementPaths.moduleFor("/api/v1/supply-chain/purchase-orders")).contains("supply_chain");
        assertThat(ModuleEntitlementPaths.moduleFor("/api/v1/recruiter/jobs/1/costs")).contains("recruiter");
        assertThat(ModuleEntitlementPaths.moduleFor("/api/v1/projects/42/members")).contains("projects");
        assertThat(ModuleEntitlementPaths.moduleFor("/api/v1/auditors/engagements")).contains("auditor");
    }

    @Test
    @DisplayName("portals, public links and shared platform services stay unmapped")
    void leavesPortalsAndSharedServicesAlone() {
        assertThat(ModuleEntitlementPaths.moduleFor("/api/v1/accountant/portal/me")).isEmpty();
        assertThat(ModuleEntitlementPaths.moduleFor("/api/v1/accountant/clients")).contains("accountant");
        assertThat(ModuleEntitlementPaths.moduleFor("/api/v1/auditor/portal/auth")).isEmpty();
        assertThat(ModuleEntitlementPaths.moduleFor("/api/v1/project-client/portal/me")).isEmpty();
        assertThat(ModuleEntitlementPaths.moduleFor("/api/v1/notifications")).isEmpty();
        assertThat(ModuleEntitlementPaths.moduleFor("/api/v1/approvals/rules")).isEmpty();
        assertThat(ModuleEntitlementPaths.moduleFor("/api/v1/audit-trail")).isEmpty();
    }

    @Test
    @DisplayName("modules without a catalogue row are never enforceable")
    void reportOnlyModules() {
        assertThat(ModuleEntitlementPaths.enforceable("projects")).isFalse();
        assertThat(ModuleEntitlementPaths.enforceable("ap")).isFalse();
        assertThat(ModuleEntitlementPaths.enforceable("pos")).isTrue();
        assertThat(ModuleEntitlementPaths.enforceable(null)).isFalse();
    }
}
