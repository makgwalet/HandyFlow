package za.co.handyflow.platform.facilitiesmanagement.domain.model;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FmWorkOrderTest {

    private FmWorkOrder newOrder() {
        return FmWorkOrder.create(TenantId.generate(), "WO-00001", UUID.randomUUID(), UUID.randomUUID(),
                null, null, "CORRECTIVE", "NORMAL", "Leaking tap in kitchen", "Jane Reception", LocalDate.now());
    }

    @Test
    void newWorkOrderStartsOpen() {
        FmWorkOrder wo = newOrder();
        assertEquals("OPEN", wo.getStatus());
        assertFalse(wo.isTerminal());
        assertFalse(wo.isInvoiced());
    }

    @Test
    void assignRequiresTechnicianOrVendor() {
        FmWorkOrder wo = newOrder();
        assertThrows(IllegalArgumentException.class, () -> wo.assign(null, null, null, null));
    }

    @Test
    void assignToVendorAloneIsValid() {
        FmWorkOrder wo = newOrder();
        wo.assign(null, null, UUID.randomUUID(), "Acme Elevators (Pty) Ltd");
        assertEquals("ASSIGNED", wo.getStatus());
        assertNull(wo.getTechnicianId());
        assertEquals("Acme Elevators (Pty) Ltd", wo.getVendorName());
    }

    @Test
    void fullLifecycleTransitionsCorrectly() {
        FmWorkOrder wo = newOrder();
        wo.assign(UUID.randomUUID(), "Sipho Dlamini", null, null);
        assertEquals("ASSIGNED", wo.getStatus());

        wo.start();
        assertEquals("IN_PROGRESS", wo.getStatus());

        wo.putOnHold("Waiting for parts");
        assertEquals("ON_HOLD", wo.getStatus());

        wo.start();
        assertEquals("IN_PROGRESS", wo.getStatus());

        wo.complete("Replaced washer", new BigDecimal("150.00"), null);
        assertEquals("COMPLETED", wo.getStatus());
        assertTrue(wo.isTerminal());
        assertNotNull(wo.getCompletedAt());
        assertTrue(wo.isBillable());
    }

    @Test
    void cannotStartAWorkOrderThatIsStillOpen() {
        FmWorkOrder wo = newOrder();
        assertThrows(IllegalStateException.class, wo::start);
    }

    @Test
    void cannotCancelACompletedInvoicedWorkOrder() {
        FmWorkOrder wo = newOrder();
        wo.assign(UUID.randomUUID(), "Sipho Dlamini", null, null);
        wo.start();
        wo.complete("Done", BigDecimal.TEN, Instant.now());
        wo.markInvoiced();
        assertTrue(wo.isInvoiced());
        assertThrows(IllegalStateException.class, () -> wo.cancel("changed my mind"));
    }

    @Test
    void cannotCancelACompletedWorkOrderEvenBeforeInvoicing() {
        FmWorkOrder wo = newOrder();
        wo.assign(UUID.randomUUID(), "Sipho Dlamini", null, null);
        wo.start();
        wo.complete("Done", BigDecimal.TEN, null);
        assertThrows(IllegalStateException.class, () -> wo.cancel("changed my mind"));
    }

    @Test
    void isBillableOnlyWhenCompletedNotInvoicedAndPositiveCost() {
        FmWorkOrder wo = newOrder();
        wo.assign(UUID.randomUUID(), "Sipho Dlamini", null, null);
        wo.start();
        wo.complete("Done", BigDecimal.ZERO, null);
        assertFalse(wo.isBillable()); // zero cost

        FmWorkOrder wo2 = newOrder();
        wo2.assign(UUID.randomUUID(), "Sipho Dlamini", null, null);
        wo2.start();
        wo2.complete("Done", BigDecimal.TEN, null);
        assertTrue(wo2.isBillable());
        wo2.markInvoiced();
        assertFalse(wo2.isBillable()); // already invoiced
    }

    @Test
    void isOverdueOnlyWhenNotTerminalAndPastScheduledDate() {
        FmWorkOrder wo = FmWorkOrder.create(TenantId.generate(), "WO-00002", UUID.randomUUID(), UUID.randomUUID(),
                null, null, "CORRECTIVE", "NORMAL", "desc", "reporter", LocalDate.now().minusDays(3));
        assertTrue(wo.isOverdue(LocalDate.now()));
        wo.cancel("no longer needed");
        assertFalse(wo.isOverdue(LocalDate.now()));
    }
}
