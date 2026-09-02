package za.co.handyflow.platform.agriculture.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AgHealthEventTest {

    private static final TenantId TENANT = TenantId.of(UUID.randomUUID());

    private AgHealthEvent newEvent(LocalDate nextDueDate) {
        return AgHealthEvent.create(TENANT, UUID.randomUUID(), null, "VACCINATION", LocalDate.now(),
                "Annual clostridial vaccination", "Multivax P Plus", "5ml", null, null, null,
                null, null, nextDueDate, null, null);
    }

    @Test
    @DisplayName("reminderAcknowledged defaults to false and acknowledgeReminder() sets it true")
    void acknowledgeReminderSetsFlag() {
        AgHealthEvent event = newEvent(LocalDate.now().plusMonths(12));
        assertFalse(event.isReminderAcknowledged());
        event.acknowledgeReminder();
        assertTrue(event.isReminderAcknowledged());
    }

    @Test
    @DisplayName("update() resets reminderAcknowledged to false even if it was already acknowledged")
    void updateResetsAcknowledgedFlag() {
        AgHealthEvent event = newEvent(LocalDate.now().plusMonths(12));
        event.acknowledgeReminder();
        assertTrue(event.isReminderAcknowledged());

        event.update("Updated description", "Multivax P Plus", "5ml", null, null, null,
                LocalDate.now().plusMonths(6), null);

        assertFalse(event.isReminderAcknowledged(),
                "changing next-due-date via update() must re-arm the reminder");
    }

    @Test
    @DisplayName("status defaults to COMPLETED and markCompleted() is idempotent")
    void statusDefaultsToCompleted() {
        AgHealthEvent event = newEvent(null);
        assertEquals("COMPLETED", event.getStatus());
        event.markCompleted();
        assertEquals("COMPLETED", event.getStatus());
    }

    @Test
    @DisplayName("create() enforces the exactly-one animal-or-group invariant")
    void createEnforcesExactlyOneTarget() {
        assertThrows(IllegalArgumentException.class, () -> AgHealthEvent.create(
                TENANT, null, null, "OBSERVATION", LocalDate.now(), "desc", null, null,
                null, null, null, null, null, null, null, null));
    }
}
