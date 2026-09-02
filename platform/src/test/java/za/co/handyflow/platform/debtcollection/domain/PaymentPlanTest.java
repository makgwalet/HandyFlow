package za.co.handyflow.platform.debtcollection.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.debtcollection.domain.model.PaymentPlan;
import za.co.handyflow.platform.debtcollection.domain.model.PaymentPlanFrequency;
import za.co.handyflow.platform.debtcollection.domain.model.PaymentPlanStatus;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PaymentPlanTest {

    private final TenantId tenantId = TenantId.generate();

    private PaymentPlan newPlan(int installments) {
        return PaymentPlan.propose(tenantId, UUID.randomUUID(), new BigDecimal("1200.00"),
                new BigDecimal("300.00"), PaymentPlanFrequency.MONTHLY, LocalDate.now(), installments, "agreed",
                UUID.randomUUID());
    }

    @Test
    @DisplayName("propose() starts ACTIVE with nextDueDate == startDate")
    void proposeStartsActive() {
        PaymentPlan p = newPlan(4);
        assertEquals(PaymentPlanStatus.ACTIVE, p.getStatus());
        assertEquals(p.getStartDate(), p.getNextDueDate());
        assertEquals(0, p.getInstallmentsPaid());
    }

    @Test
    @DisplayName("propose() rejects non-positive totalAgreedAmount/installmentAmount/numberOfInstallments")
    void proposeValidatesAmounts() {
        assertThrows(IllegalArgumentException.class, () -> PaymentPlan.propose(tenantId, UUID.randomUUID(),
                BigDecimal.ZERO, new BigDecimal("100"), PaymentPlanFrequency.MONTHLY, LocalDate.now(), 4, null,
                UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class, () -> PaymentPlan.propose(tenantId, UUID.randomUUID(),
                new BigDecimal("1200"), BigDecimal.ZERO, PaymentPlanFrequency.MONTHLY, LocalDate.now(), 4, null,
                UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class, () -> PaymentPlan.propose(tenantId, UUID.randomUUID(),
                new BigDecimal("1200"), new BigDecimal("300"), PaymentPlanFrequency.MONTHLY, LocalDate.now(), 0,
                null, UUID.randomUUID()));
    }

    @Test
    @DisplayName("markInstallmentPaid() advances nextDueDate and completes on the final installment")
    void markInstallmentPaidCompletesOnFinal() {
        PaymentPlan p = newPlan(2);
        p.markInstallmentPaid();
        assertEquals(1, p.getInstallmentsPaid());
        assertEquals(PaymentPlanStatus.ACTIVE, p.getStatus());
        assertNotNull(p.getNextDueDate());

        p.markInstallmentPaid();
        assertEquals(2, p.getInstallmentsPaid());
        assertEquals(PaymentPlanStatus.COMPLETED, p.getStatus());
        assertNull(p.getNextDueDate());
    }

    @Test
    @DisplayName("markInstallmentPaid() rejects a plan that is not ACTIVE")
    void markInstallmentPaidRejectedWhenNotActive() {
        PaymentPlan p = newPlan(1);
        p.markInstallmentPaid(); // -> COMPLETED
        assertThrows(IllegalStateException.class, p::markInstallmentPaid);
    }

    @Test
    @DisplayName("markDefaulted() moves an ACTIVE plan to DEFAULTED")
    void markDefaultedMovesToDefaulted() {
        PaymentPlan p = newPlan(3);
        p.markDefaulted("missed two installments");
        assertEquals(PaymentPlanStatus.DEFAULTED, p.getStatus());
        assertThrows(IllegalStateException.class, () -> p.markDefaulted("again"));
    }

    @Test
    @DisplayName("cancel() rejects a COMPLETED plan")
    void cancelRejectsCompletedPlan() {
        PaymentPlan p = newPlan(1);
        p.markInstallmentPaid(); // -> COMPLETED
        assertThrows(IllegalStateException.class, () -> p.cancel("changed mind"));
    }

    @Test
    @DisplayName("cancel() moves an ACTIVE plan to CANCELLED")
    void cancelMovesActiveToCancelled() {
        PaymentPlan p = newPlan(3);
        p.cancel("debtor withdrew");
        assertEquals(PaymentPlanStatus.CANCELLED, p.getStatus());
    }
}
