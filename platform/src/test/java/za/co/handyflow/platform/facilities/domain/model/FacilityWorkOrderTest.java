package za.co.handyflow.platform.facilities.domain.model;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FacilityWorkOrderTest {

    private FacilityWorkOrder newOrder() {
        return FacilityWorkOrder.create(TenantId.generate(), "WO-00001", UUID.randomUUID(), UUID.randomUUID(),
                null, "CORRECTIVE", "NORMAL", "Leaking tap in kitchen", "Jane Reception", LocalDate.now());
    }

    @Test
    void newWorkOrderStartsOpen() {
        FacilityWorkOrder wo = newOrder();
        assertEquals("OPEN", wo.getStatus());
        assertFalse(wo.isTerminal());
    }

    @Test
    void assignRequiresTechnicianOrVendor() {
        FacilityWorkOrder wo = newOrder();
        assertThrows(IllegalArgumentException.class, () -> wo.assign(null, null, null, null));
    }

    @Test
    void fullLifecycleTransitionsCorrectly() {
        FacilityWorkOrder wo = newOrder();
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
    }

    @Test
    void cannotStartAWorkOrderThatIsStillOpen() {
        FacilityWorkOrder wo = newOrder();
        assertThrows(IllegalStateException.class, wo::start);
    }

    @Test
    void cannotCancelACompletedWorkOrder() {
        FacilityWorkOrder wo = newOrder();
        wo.assign(UUID.randomUUID(), "Sipho Dlamini", null, null);
        wo.start();
        wo.complete("Done", BigDecimal.TEN, null);
        assertThrows(IllegalStateException.class, () -> wo.cancel("changed my mind"));
    }

    @Test
    void isOverdueOnlyWhenNotTerminalAndPastScheduledDate() {
        FacilityWorkOrder wo = FacilityWorkOrder.create(TenantId.generate(), "WO-00002", UUID.randomUUID(),
                null, null, "CORRECTIVE", "NORMAL", "desc", "reporter", LocalDate.now().minusDays(3));
        assertTrue(wo.isOverdue(LocalDate.now()));
        wo.cancel("no longer needed");
        assertFalse(wo.isOverdue(LocalDate.now()));
    }
}
