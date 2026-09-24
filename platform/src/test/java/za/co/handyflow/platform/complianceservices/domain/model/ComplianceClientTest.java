package za.co.handyflow.platform.complianceservices.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.TenantId;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ComplianceClientTest {

    private static final TenantId TENANT = TenantId.generate();
    private static final UUID USER = UUID.randomUUID();

    @Test
    @DisplayName("create() defaults to ACTIVE status")
    void create_defaultsToActive() {
        ComplianceClient c = ComplianceClient.create(TENANT, "Acme Construction", null, null, null, null, USER);
        assertThat(c.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("create() rejects a blank name")
    void create_rejectsBlankName() {
        assertThatThrownBy(() -> ComplianceClient.create(TENANT, "  ", null, null, null, null, USER))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("deactivate() then reactivate() round-trips the status correctly")
    void deactivateThenReactivate_roundTrips() {
        ComplianceClient c = ComplianceClient.create(TENANT, "Acme Construction", null, null, null, null, USER);

        c.deactivate(USER);
        assertThat(c.getStatus()).isEqualTo("INACTIVE");

        c.reactivate(USER);
        assertThat(c.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("update() rejects a blank name, same as create()")
    void update_rejectsBlankName() {
        ComplianceClient c = ComplianceClient.create(TENANT, "Acme Construction", null, null, null, null, USER);

        assertThatThrownBy(() -> c.update("", "ops@acme.com", null, null, USER))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
