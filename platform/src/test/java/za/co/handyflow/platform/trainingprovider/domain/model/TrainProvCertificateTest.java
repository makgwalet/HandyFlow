package za.co.handyflow.platform.trainingprovider.domain.model;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TrainProvCertificateTest {

    private TrainProvCertificate newCertificate(LocalDate expiryDate) {
        return TrainProvCertificate.create(TenantId.generate(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "Jane Delegate", "Acme Corp", "Advanced Rigging", "US12345", "CERT-00001", LocalDate.now(), expiryDate);
    }

    @Test
    void newCertificateStartsValid() {
        TrainProvCertificate certificate = newCertificate(LocalDate.now().plusYears(2));
        assertEquals("VALID", certificate.getStatus());
        assertFalse(certificate.isExpired());
    }

    @Test
    void pastExpiryDateReportsExpired() {
        TrainProvCertificate certificate = newCertificate(LocalDate.now().minusDays(1));
        assertTrue(certificate.isExpired());
    }

    @Test
    void revokeSetsStatusAndReason() {
        TrainProvCertificate certificate = newCertificate(LocalDate.now().plusYears(1));
        certificate.revoke("Issued in error");
        assertEquals("REVOKED", certificate.getStatus());
        assertNotNull(certificate.getRevokedAt());
        assertThrows(IllegalStateException.class, () -> certificate.revoke("again"));
    }

    @Test
    void markExpiredIsANoOpOnceRevoked() {
        TrainProvCertificate certificate = newCertificate(LocalDate.now().minusDays(1));
        certificate.revoke("test");
        certificate.markExpired();
        assertEquals("REVOKED", certificate.getStatus());
    }

    @Test
    void isExpiringWithinRespectsWindowAndValidStatus() {
        TrainProvCertificate certificate = newCertificate(LocalDate.now().plusDays(10));
        assertTrue(certificate.isExpiringWithin(30));
        assertFalse(certificate.isExpiringWithin(5));
        certificate.revoke("test");
        assertFalse(certificate.isExpiringWithin(30));
    }
}
