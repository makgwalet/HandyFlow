package za.co.handyflow.platform.training.domain.model;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TrainingCertificateTest {

    private TrainingCertificate newCertificate(LocalDate expiryDate) {
        return TrainingCertificate.create(TenantId.generate(), UUID.randomUUID(), UUID.randomUUID(),
                "Jane Dlamini", "First Aid Level 1", "CERT-00001", LocalDate.now(), expiryDate);
    }

    @Test
    void newCertificateStartsValid() {
        TrainingCertificate certificate = newCertificate(LocalDate.now().plusYears(1));
        assertEquals("VALID", certificate.getStatus());
        assertFalse(certificate.isExpired());
    }

    @Test
    void noExpiryDateMeansNeverExpires() {
        TrainingCertificate certificate = newCertificate(null);
        assertFalse(certificate.isExpired());
        assertFalse(certificate.isExpiringWithin(30));
    }

    @Test
    void isExpiringWithinRespectsWindow() {
        TrainingCertificate certificate = newCertificate(LocalDate.now().plusDays(10));
        assertTrue(certificate.isExpiringWithin(30));
        assertFalse(certificate.isExpiringWithin(5));
    }

    @Test
    void pastExpiryDateReportsExpired() {
        TrainingCertificate certificate = newCertificate(LocalDate.now().minusDays(1));
        assertTrue(certificate.isExpired());
    }

    @Test
    void revokeSetsStatusAndReason() {
        TrainingCertificate certificate = newCertificate(LocalDate.now().plusYears(1));
        certificate.revoke("Issued in error");
        assertEquals("REVOKED", certificate.getStatus());
        assertEquals("Issued in error", certificate.getRevokedReason());
        assertNotNull(certificate.getRevokedAt());
        assertThrows(IllegalStateException.class, () -> certificate.revoke("again"));
    }

    @Test
    void markExpiredIsANoOpOnceRevokedAndIdempotentOnceExpired() {
        TrainingCertificate certificate = newCertificate(LocalDate.now().minusDays(1));
        certificate.revoke("test");
        certificate.markExpired();
        assertEquals("REVOKED", certificate.getStatus());

        TrainingCertificate expiring = newCertificate(LocalDate.now().minusDays(1));
        expiring.markExpired();
        assertEquals("EXPIRED", expiring.getStatus());
        expiring.markExpired();
        assertEquals("EXPIRED", expiring.getStatus());
    }

    @Test
    void isExpiringWithinIsFalseOnceNoLongerValid() {
        TrainingCertificate certificate = newCertificate(LocalDate.now().plusDays(5));
        certificate.revoke("test");
        assertFalse(certificate.isExpiringWithin(30));
    }
}
