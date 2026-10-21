package za.co.handyflow.platform.fleet.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.fleet.domain.model.DriverDocumentType;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DriverDocumentRulesTest {

    private static final LocalDate D1 = LocalDate.of(2026, 1, 1), D2 = LocalDate.of(2027, 1, 1);

    private static String message(Runnable r) {
        return assertThrows(IllegalArgumentException.class, r::run).getMessage();
    }

    @Test
    void aPhotoMustBeAnImage() {
        assertDoesNotThrow(() -> DriverDocumentRules.check(DriverDocumentType.PHOTO, "image/png", null, null, null));
        assertEquals("A driver photo must be a JPEG, PNG or WebP image",
                message(() -> DriverDocumentRules.check(DriverDocumentType.PHOTO, "application/pdf", null, null, null)));
    }

    @Test
    void aTrainingCertificateNeedsACourseName() {
        assertDoesNotThrow(() -> DriverDocumentRules.check(DriverDocumentType.TRAINING_CERTIFICATE, "application/pdf", "Defensive driving", null, null));
        assertEquals("Name the course for a training certificate",
                message(() -> DriverDocumentRules.check(DriverDocumentType.TRAINING_CERTIFICATE, "application/pdf", " ", null, null)));
    }

    @Test
    void aMedicalCertificateNeedsAnExpiryDate() {
        assertDoesNotThrow(() -> DriverDocumentRules.check(DriverDocumentType.MEDICAL_CERTIFICATE, "application/pdf", null, null, D2));
        assertEquals("A medical certificate needs an expiry date",
                message(() -> DriverDocumentRules.check(DriverDocumentType.MEDICAL_CERTIFICATE, "application/pdf", null, null, null)));
    }

    @Test
    void expiryCannotBeBeforeIssue() {
        assertEquals("Expiry date cannot be before the issue date",
                message(() -> DriverDocumentRules.check(DriverDocumentType.PRDP, "application/pdf", null, D2, D1)));
        assertDoesNotThrow(() -> DriverDocumentRules.check(DriverDocumentType.PRDP, "application/pdf", null, D1, D2));
    }
}
