package za.co.handyflow.platform.training.domain.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class CertificateExpiryRulesTest {

    private static final LocalDate EXPIRY = LocalDate.of(2026, 12, 31);

    @Test
    void warnsOnlyOnTheDaysThatMatter() {
        for (int left : new int[]{30, 14, 7, 1}) assertTrue(CertificateExpiryRules.warnToday(EXPIRY, EXPIRY.minusDays(left)), "day " + left);
        for (int left : new int[]{31, 29, 20, 13, 8, 6, 5, 2, 0}) assertFalse(CertificateExpiryRules.warnToday(EXPIRY, EXPIRY.minusDays(left)), "day " + left);
    }

    @Test
    void aLapsedOrUndatedCertificateIsNotWarnedAbout() {
        assertFalse(CertificateExpiryRules.warnToday(EXPIRY, EXPIRY.plusDays(1)));
        assertFalse(CertificateExpiryRules.warnToday(null, EXPIRY));
    }

    @Test
    void aCertificateLapsesTheDayAfterItsExpiryDate() {
        assertFalse(CertificateExpiryRules.lapsed(EXPIRY, EXPIRY));
        assertTrue(CertificateExpiryRules.lapsed(EXPIRY, EXPIRY.plusDays(1)));
        assertFalse(CertificateExpiryRules.lapsed(null, EXPIRY));
    }

    @Test
    void theWordsSayTomorrowOrTheNumberOfDays() {
        assertEquals("Your First Aid certificate expires tomorrow", CertificateExpiryRules.holderTitle("First Aid", 1));
        assertEquals("Your First Aid certificate expires in 14 days", CertificateExpiryRules.holderTitle("First Aid", 14));
        assertTrue(CertificateExpiryRules.holderWarning("First Aid", "TC-0001", EXPIRY).contains("2026-12-31"));
    }
}
