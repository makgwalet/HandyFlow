package za.co.handyflow.platform.compliancetender.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 1 domain model tests for compliancetender — see the strategic
 * roadmap backlog, Part 6, for the full scoping. Covers the same
 * expiry-window and status-transition behaviours already established and
 * tested for the closest existing analog, FacilityComplianceCertificate.
 */
class ComplianceRegistrationTest {

    private static final TenantId TENANT = TenantId.generate();
    private static final UUID CREATED_BY = UUID.randomUUID();

    @Test
    @DisplayName("create() defaults to ACTIVE status")
    void create_defaultsToActive() {
        ComplianceRegistration r = ComplianceRegistration.create(TENANT, "cipc", "Business Registration",
                "2020/123456/07", LocalDate.of(2020, 1, 1), null, null, CREATED_BY);

        assertThat(r.getStatus()).isEqualTo("ACTIVE");
        assertThat(r.getAuthority()).isEqualTo("CIPC"); // uppercased
    }

    @Test
    @DisplayName("create() rejects an expiry date before the issued date")
    void create_rejectsExpiryBeforeIssued() {
        assertThatThrownBy(() -> ComplianceRegistration.create(TENANT, "SARS", "VAT", null,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 1, 1), null, CREATED_BY))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("create() allows a null expiry date — some registrations never expire")
    void create_allowsNullExpiry() {
        ComplianceRegistration r = ComplianceRegistration.create(TENANT, "CIPC", "Business Registration",
                "2020/123456/07", LocalDate.of(2020, 1, 1), null, null, CREATED_BY);

        assertThat(r.getExpiryDate()).isNull();
        assertThat(r.isExpiringWithin(30)).isFalse();
        assertThat(r.isExpired()).isFalse();
    }

    @Test
    @DisplayName("isExpiringWithin returns true only inside the window, for an ACTIVE registration")
    void isExpiringWithin_onlyInsideWindowAndActive() {
        ComplianceRegistration expiringSoon = ComplianceRegistration.create(TENANT, "PSIRA", "Business Registration",
                null, LocalDate.now().minusYears(1), LocalDate.now().plusDays(10), null, CREATED_BY);
        ComplianceRegistration expiringLater = ComplianceRegistration.create(TENANT, "PSIRA", "Business Registration",
                null, LocalDate.now().minusYears(1), LocalDate.now().plusDays(90), null, CREATED_BY);

        assertThat(expiringSoon.isExpiringWithin(30)).isTrue();
        assertThat(expiringLater.isExpiringWithin(30)).isFalse();
    }

    @Test
    @DisplayName("markExpired transitions ACTIVE to EXPIRED, and is a no-op otherwise")
    void markExpired_transitionsFromActiveOnly() {
        ComplianceRegistration r = ComplianceRegistration.create(TENANT, "CSD", "Supplier Registration",
                null, LocalDate.now().minusYears(1), LocalDate.now().minusDays(1), null, CREATED_BY);

        r.markExpired();
        assertThat(r.getStatus()).isEqualTo("EXPIRED");

        r.markExpired(); // no-op, doesn't throw
        assertThat(r.getStatus()).isEqualTo("EXPIRED");
    }

    @Test
    @DisplayName("isExpired is true for an ACTIVE registration whose expiry date has already passed")
    void isExpired_trueWhenActiveAndPastExpiry() {
        ComplianceRegistration r = ComplianceRegistration.create(TENANT, "CIDB", "Grade 4GB",
                null, LocalDate.now().minusYears(3), LocalDate.now().minusDays(1), null, CREATED_BY);

        assertThat(r.isExpired()).isTrue();
    }

    @Test
    @DisplayName("update() rejects an expiry date before the issued date, same as create()")
    void update_rejectsExpiryBeforeIssued() {
        ComplianceRegistration r = ComplianceRegistration.create(TENANT, "SARS", "Income Tax", null,
                LocalDate.of(2026, 1, 1), null, null, CREATED_BY);

        assertThatThrownBy(() -> r.update(null, "ACTIVE", LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 1, 1), null, CREATED_BY))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
