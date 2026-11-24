package za.co.handyflow.platform.complianceservices.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.TenantId;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ComplianceClientSiteTest {

    private static final UUID USER = UUID.randomUUID();

    @Test @DisplayName("a new site is active and defaults to a branch")
    void defaults() {
        ComplianceClientSite s = ComplianceClientSite.create(TenantId.generate(), UUID.randomUUID(), " Durban depot ", null, "5 Quay Rd", 14, USER);
        assertThat(s.getName()).isEqualTo("Durban depot");
        assertThat(s.getSiteType()).isEqualTo("BRANCH");
        assertThat(s.isActive()).isTrue();
    }

    @Test @DisplayName("a name is required and the type must be known")
    void validation() {
        assertThatThrownBy(() -> ComplianceClientSite.create(TenantId.generate(), UUID.randomUUID(), "", null, null, null, USER)).hasMessageContaining("name");
        assertThatThrownBy(() -> ComplianceClientSite.create(TenantId.generate(), UUID.randomUUID(), "X", "CASTLE", null, null, USER)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ComplianceClientSite.create(TenantId.generate(), UUID.randomUUID(), "X", null, null, -2, USER)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test @DisplayName("a site can be switched off and on")
    void toggle() {
        ComplianceClientSite s = ComplianceClientSite.create(TenantId.generate(), UUID.randomUUID(), "X", "head_office", null, null, USER);
        assertThat(s.getSiteType()).isEqualTo("HEAD_OFFICE");
        s.setActive(false, USER);
        assertThat(s.isActive()).isFalse();
        s.setActive(true, USER);
        assertThat(s.isActive()).isTrue();
    }
}
