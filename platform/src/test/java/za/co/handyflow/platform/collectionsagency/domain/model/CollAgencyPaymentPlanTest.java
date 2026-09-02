package za.co.handyflow.platform.collectionsagency.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CollAgencyPaymentPlanTest {

    private final UUID tenantId = UUID.randomUUID();
    private final UUID debtorAccountId = UUID.randomUUID();

    private CollAgencyPaymentPlan newPlan(int installments) {
        return CollAgencyPaymentPlan.propose(tenantId, debtorAccountId, new BigDecimal("1000.00"),
                new BigDecimal("250.00"), "MONTHLY", LocalDate.now(), installments, "notes");
    }

    @Test
    void proposeRejectsNonPositiveTotalAgreedAmount() {
        assertThrows(IllegalArgumentException.class, () -> CollAgencyPaymentPlan.propose(tenantId, debtorAccountId,
                BigDecimal.ZERO, new BigDecimal("100"), "MONTHLY", LocalDate.now(), 4, null));
    }

    @Test
    void proposeRejectsMissingFrequency() {
        assertThrows(IllegalArgumentException.class, () -> CollAgencyPaymentPlan.propose(tenantId, debtorAccountId,
                new BigDecimal("1000"), new BigDecimal("250"), " ", LocalDate.now(), 4, null));
    }

    @Test
    void newPlanStartsActiveWithZeroInstallmentsPaid() {
        CollAgencyPaymentPlan p = newPlan(4);
        assertEquals("ACTIVE", p.getStatus());
        assertEquals(0, p.getInstallmentsPaid());
        assertEquals(p.getStartDate(), p.getNextDueDate());
    }

    @Test
    void markInstallmentPaidAdvancesNextDueDateForMonthly() {
        CollAgencyPaymentPlan p = newPlan(4);
        LocalDate before = p.getNextDueDate();
        p.markInstallmentPaid();
        assertEquals(1, p.getInstallmentsPaid());
        assertEquals(before.plusMonths(1), p.getNextDueDate());
        assertEquals("ACTIVE", p.getStatus());
    }

    @Test
    void markInstallmentPaidOnFinalInstallmentCompletesThePlan() {
        CollAgencyPaymentPlan p = newPlan(2);
        p.markInstallmentPaid();
        p.markInstallmentPaid();
        assertEquals("COMPLETED", p.getStatus());
        assertNull(p.getNextDueDate());
    }

    @Test
    void markInstallmentPaidRejectedOnNonActivePlan() {
        CollAgencyPaymentPlan p = newPlan(1);
        p.markInstallmentPaid();
        assertEquals("COMPLETED", p.getStatus());
        assertThrows(IllegalStateException.class, p::markInstallmentPaid);
    }

    @Test
    void markDefaultedRejectedOnNonActivePlan() {
        CollAgencyPaymentPlan p = newPlan(1);
        p.cancel("client withdrew");
        assertThrows(IllegalStateException.class, () -> p.markDefaulted("missed payment"));
    }

    @Test
    void cancelRejectedOnCompletedPlan() {
        CollAgencyPaymentPlan p = newPlan(1);
        p.markInstallmentPaid();
        assertThrows(IllegalStateException.class, () -> p.cancel("too late"));
    }
}
