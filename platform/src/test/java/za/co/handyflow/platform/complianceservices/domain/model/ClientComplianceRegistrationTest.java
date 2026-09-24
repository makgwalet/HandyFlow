package za.co.handyflow.platform.complianceservices.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Mirrors compliancetender's own ComplianceRegistrationTest almost exactly — same behaviour, deliberately parallel entity. */
class ClientComplianceRegistrationTest {

    private static final TenantId TENANT = TenantId.generate();
    private static final UUID CLIENT_ID = UUID.randomUUID();
    private static final UUID CREATED_BY = UUID.randomUUID();

    @Test
    @DisplayName("create() defaults to ACTIVE status")
    void create_defaultsToActive() {
        var r = ClientComplianceRegistration.create(TENANT, CLIENT_ID, "cipc", "Business Registration",
                "2020/123456/07", LocalDate.of(2020, 1, 1), null, null, CREATED_BY);

        assertThat(r.getStatus()).isEqualTo("ACTIVE");
        assertThat(r.getAuthority()).isEqualTo("CIPC");
        assertThat(r.getClientId()).isEqualTo(CLIENT_ID);
    }

    @Test
    @DisplayName("create() rejects an expiry date before the issued date")
    void create_rejectsExpiryBeforeIssued() {
        assertThatThrownBy(() -> ClientComplianceRegistration.create(TENANT, CLIENT_ID, "SARS", "VAT", null,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 1, 1), null, CREATED_BY))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("isExpiringWithin returns true only inside the window, for an ACTIVE registration")
    void isExpiringWithin_onlyInsideWindowAndActive() {
        var expiringSoon = ClientComplianceRegistration.create(TENANT, CLIENT_ID, "PSIRA", "Business Registration",
                null, LocalDate.now().minusYears(1), LocalDate.now().plusDays(10), null, CREATED_BY);
        var expiringLater = ClientComplianceRegistration.create(TENANT, CLIENT_ID, "PSIRA", "Business Registration",
                null, LocalDate.now().minusYears(1), LocalDate.now().plusDays(90), null, CREATED_BY);

        assertThat(expiringSoon.isExpiringWithin(30)).isTrue();
        assertThat(expiringLater.isExpiringWithin(30)).isFalse();
    }

    @Test
    @DisplayName("markExpired transitions ACTIVE to EXPIRED, and is a no-op otherwise")
    void markExpired_transitionsFromActiveOnly() {
        var r = ClientComplianceRegistration.create(TENANT, CLIENT_ID, "CSD", "Supplier Registration",
                null, LocalDate.now().minusYears(1), LocalDate.now().minusDays(1), null, CREATED_BY);

        r.markExpired();
        assertThat(r.getStatus()).isEqualTo("EXPIRED");

        r.markExpired();
        assertThat(r.getStatus()).isEqualTo("EXPIRED");
    }
}
