package za.co.handyflow.platform.facilities.domain.model;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FacilityCorrectiveActionTest {

    private final LocalDate today = LocalDate.of(2026, 10, 27);

    private FacilityCorrectiveAction action(LocalDate due) {
        return FacilityCorrectiveAction.create(TenantId.generate(), "manual", null, UUID.randomUUID(), null,
                " Replace the fire hose ", null, false, due, UUID.randomUUID());
    }

    @Test
    void defaultsToNormalPriorityAndOpen() {
        FacilityCorrectiveAction a = action(today.plusDays(5));
        assertEquals("OPEN", a.getStatus());
        assertEquals("NORMAL", a.getPriority());
        assertEquals("MANUAL", a.getSourceType());
        assertEquals("Replace the fire hose", a.getDescription());
    }

    @Test
    void validatesItsInputs() {
        TenantId t = TenantId.generate();
        UUID s = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> FacilityCorrectiveAction.create(t, "BOGUS", null, s, null, "x", null, false, today, null));
        assertThrows(IllegalArgumentException.class, () -> FacilityCorrectiveAction.create(t, "MANUAL", null, s, null, " ", null, false, today, null));
        assertThrows(IllegalArgumentException.class, () -> FacilityCorrectiveAction.create(t, "MANUAL", null, s, null, "x", "SOON", false, today, null));
        assertThrows(IllegalArgumentException.class, () -> FacilityCorrectiveAction.create(t, "MANUAL", null, s, null, "x", null, false, null, null));
        assertThrows(IllegalArgumentException.class, () -> FacilityCorrectiveAction.create(t, "MANUAL", null, s, null, "x".repeat(1001), null, false, today, null));
    }

    @Test
    void overdueOnlyWhilstUnfinishedAndPastDue() {
        FacilityCorrectiveAction a = action(today.minusDays(1));
        assertTrue(a.isOverdue(today));
        assertFalse(action(today).isOverdue(today));
        a.close("Done");
        assertFalse(a.isOverdue(today));
    }

    @Test
    void linkingAWorkOrderMovesItInProgressOnce() {
        FacilityCorrectiveAction a = action(today);
        UUID wo = UUID.randomUUID();
        a.linkWorkOrder(wo);
        assertEquals("IN_PROGRESS", a.getStatus());
        assertEquals(wo, a.getWorkOrderId());
        assertThrows(IllegalStateException.class, () -> a.linkWorkOrder(UUID.randomUUID()));
    }

    @Test
    void closingAndCancellingNeedANoteAndAreFinal() {
        FacilityCorrectiveAction a = action(today);
        assertThrows(IllegalArgumentException.class, () -> a.close(" "));
        a.close(" Replaced ");
        assertEquals("CLOSED", a.getStatus());
        assertEquals("Replaced", a.getClosureNote());
        assertNotNull(a.getClosedAt());
        assertThrows(IllegalStateException.class, () -> a.close("again"));
        assertThrows(IllegalStateException.class, () -> a.cancel("no"));
        assertThrows(IllegalStateException.class, () -> a.reschedule(today.plusDays(3)));
        assertThrows(IllegalStateException.class, () -> a.linkWorkOrder(UUID.randomUUID()));

        FacilityCorrectiveAction b = action(today);
        assertThrows(IllegalArgumentException.class, () -> b.cancel(null));
        b.cancel("Duplicate");
        assertEquals("CANCELLED", b.getStatus());
        assertTrue(b.isTerminal());
    }

    @Test
    void reschedulingMovesTheDueDate() {
        FacilityCorrectiveAction a = action(today.minusDays(2));
        a.reschedule(today.plusDays(7));
        assertEquals(today.plusDays(7), a.getDueDate());
        assertFalse(a.isOverdue(today));
        assertThrows(IllegalArgumentException.class, () -> a.reschedule(null));
    }
}
