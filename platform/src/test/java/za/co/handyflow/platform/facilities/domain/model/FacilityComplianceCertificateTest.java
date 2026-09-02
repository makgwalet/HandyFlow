package za.co.handyflow.platform.facilities.domain.model;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FacilityComplianceCertificateTest {

    private FacilityComplianceCertificate newCertificate(LocalDate expiryDate) {
        return FacilityComplianceCertificate.create(TenantId.generate(), UUID.randomUUID(), null,
                "ELECTRICAL_COC", "COC-2026-001", "ABC Electrical", LocalDate.now(), expiryDate, null);
    }

    @Test
    void createRejectsExpiryBeforeIssueDate() {
        assertThrows(IllegalArgumentException.class, () ->
                FacilityComplianceCertificate.create(TenantId.generate(), UUID.randomUUID(), null,
                        "ELECTRICAL_COC", null, null, LocalDate.now(), LocalDate.now().minusDays(1), null));
    }

    @Test
    void newCertificateStartsValid() {
        FacilityComplianceCertificate cert = newCertificate(LocalDate.now().plusYears(1));
        assertEquals("VALID", cert.getStatus());
        assertFalse(cert.isExpired());
    }

    @Test
    void pastExpiryDateReportsExpired() {
        FacilityComplianceCertificate cert = newCertificate(LocalDate.now().minusDays(1));
        assertTrue(cert.isExpired());
    }

    @Test
    void markExpiredTransitionsFromValidOnly() {
        FacilityComplianceCertificate cert = newCertificate(LocalDate.now().minusDays(1));
        cert.markExpired();
        assertEquals("EXPIRED", cert.getStatus());

        FacilityComplianceCertificate revoked = newCertificate(LocalDate.now().plusDays(10));
        revoked.revoke("Issued in error");
        revoked.markExpired();
        assertEquals("REVOKED", revoked.getStatus());
    }

    @Test
    void revokeIsTerminal() {
        FacilityComplianceCertificate cert = newCertificate(LocalDate.now().plusYears(1));
        cert.revoke("test");
        assertThrows(IllegalStateException.class, () -> cert.revoke("again"));
    }

    @Test
    void isExpiringWithinRespectsWindowAndStatus() {
        FacilityComplianceCertificate cert = newCertificate(LocalDate.now().plusDays(10));
        assertTrue(cert.isExpiringWithin(30));
        assertFalse(cert.isExpiringWithin(5));
        cert.revoke("test");
        assertFalse(cert.isExpiringWithin(30));
    }
}
