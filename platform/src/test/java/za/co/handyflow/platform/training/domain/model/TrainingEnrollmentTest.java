package za.co.handyflow.platform.training.domain.model;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TrainingEnrollmentTest {

    private TrainingEnrollment newEnrollment() {
        return TrainingEnrollment.create(TenantId.generate(), UUID.randomUUID(), UUID.randomUUID(),
                "Jane Dlamini", "EMP001", null);
    }

    @Test
    void newEnrollmentStartsEnrolled() {
        TrainingEnrollment enrollment = newEnrollment();
        assertEquals("ENROLLED", enrollment.getStatus());
        assertFalse(enrollment.isTerminal());
        assertFalse(enrollment.isEligibleForCertificate());
    }

    @Test
    void markAttendedRequiresEnrolled() {
        TrainingEnrollment enrollment = newEnrollment();
        enrollment.markAttended();
        assertEquals("ATTENDED", enrollment.getStatus());
        assertThrows(IllegalStateException.class, enrollment::markAttended);
    }

    @Test
    void markNoShowRequiresEnrolled() {
        TrainingEnrollment enrollment = newEnrollment();
        enrollment.markNoShow();
        assertEquals("NO_SHOW", enrollment.getStatus());
    }

    @Test
    void completeWithPassedMovesToCompletedAndIsEligibleForCertificate() {
        TrainingEnrollment enrollment = newEnrollment();
        enrollment.complete(new BigDecimal("85"), true);
        assertEquals("COMPLETED", enrollment.getStatus());
        assertTrue(enrollment.isEligibleForCertificate());
        assertNotNull(enrollment.getCompletedAt());
        assertTrue(enrollment.isTerminal());
    }

    @Test
    void completeWithoutPassingMovesToFailedAndIsNotEligible() {
        TrainingEnrollment enrollment = newEnrollment();
        enrollment.complete(new BigDecimal("40"), false);
        assertEquals("FAILED", enrollment.getStatus());
        assertFalse(enrollment.isEligibleForCertificate());
    }

    @Test
    void cancelRequiresNonTerminal() {
        TrainingEnrollment enrollment = newEnrollment();
        enrollment.complete(new BigDecimal("85"), true);
        assertThrows(IllegalStateException.class, () -> enrollment.cancel("changed mind"));
    }

    @Test
    void completeRequiresNonTerminal() {
        TrainingEnrollment enrollment = newEnrollment();
        enrollment.cancel("no longer needed");
        assertThrows(IllegalStateException.class, () -> enrollment.complete(BigDecimal.TEN, true));
    }
}
