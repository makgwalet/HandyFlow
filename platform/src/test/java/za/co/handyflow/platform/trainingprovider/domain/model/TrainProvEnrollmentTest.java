package za.co.handyflow.platform.trainingprovider.domain.model;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TrainProvEnrollmentTest {

    private TrainProvEnrollment newEnrollment() {
        return TrainProvEnrollment.create(TenantId.generate(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "Jane Delegate", null);
    }

    @Test
    void newEnrollmentStartsEnrolledAndNotInvoiced() {
        TrainProvEnrollment enrollment = newEnrollment();
        assertEquals("ENROLLED", enrollment.getStatus());
        assertFalse(enrollment.isInvoiced());
        assertTrue(enrollment.isBillable());
    }

    @Test
    void completeWithPassedIsEligibleForCertificate() {
        TrainProvEnrollment enrollment = newEnrollment();
        enrollment.complete(new BigDecimal("80"), true);
        assertEquals("COMPLETED", enrollment.getStatus());
        assertTrue(enrollment.isEligibleForCertificate());
    }

    @Test
    void cancelledEnrollmentIsNotBillable() {
        TrainProvEnrollment enrollment = newEnrollment();
        enrollment.cancel("withdrawn");
        assertFalse(enrollment.isBillable());
    }

    @Test
    void markInvoicedMakesItNoLongerBillable() {
        TrainProvEnrollment enrollment = newEnrollment();
        enrollment.markInvoiced();
        assertTrue(enrollment.isInvoiced());
        assertFalse(enrollment.isBillable());
    }

    @Test
    void cannotCancelAnAlreadyInvoicedEnrollment() {
        TrainProvEnrollment enrollment = newEnrollment();
        enrollment.markInvoiced();
        assertThrows(IllegalStateException.class, () -> enrollment.cancel("too late"));
    }

    @Test
    void markAttendedRequiresEnrolled() {
        TrainProvEnrollment enrollment = newEnrollment();
        enrollment.markAttended();
        assertEquals("ATTENDED", enrollment.getStatus());
        assertThrows(IllegalStateException.class, enrollment::markAttended);
    }

    @Test
    void completeRequiresNonTerminal() {
        TrainProvEnrollment enrollment = newEnrollment();
        enrollment.cancel("no longer needed");
        assertThrows(IllegalStateException.class, () -> enrollment.complete(BigDecimal.TEN, true));
    }
}
