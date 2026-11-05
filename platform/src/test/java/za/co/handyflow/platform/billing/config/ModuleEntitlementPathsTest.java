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
}
