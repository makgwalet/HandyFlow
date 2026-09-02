package za.co.handyflow.platform.agriculture.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure entity-behavior tests — no Spring context, matching AgHealthEventTest's
 * own plain-JUnit convention, since AgScoutingRecord is the Crops
 * sub-domain's direct structural counterpart to AgHealthEvent.
 */
class AgScoutingRecordTest {

    private static final TenantId TENANT = TenantId.of(UUID.randomUUID());

    private AgScoutingRecord newRecord(LocalDate followUpDate) {
        return AgScoutingRecord.create(TENANT, UUID.randomUUID(), LocalDate.now(), "PEST", "MEDIUM",
                "Aphids observed on lower leaves", "Spray next week", null, null, followUpDate, null);
    }

    @Test
    @DisplayName("status defaults to OPEN; resolve() then reopen() round-trips status")
    void resolveThenReopenRoundTrips() {
        AgScoutingRecord record = newRecord(null);
        assertEquals("OPEN", record.getStatus());

        record.resolve();
        assertEquals("RESOLVED", record.getStatus());

        record.reopen();
        assertEquals("OPEN", record.getStatus());
    }

    @Test
    @DisplayName("followUpAcknowledged defaults to false and acknowledgeFollowUp() sets it true")
    void acknowledgeFollowUpSetsFlag() {
        AgScoutingRecord record = newRecord(LocalDate.now().plusDays(7));
        assertFalse(record.isFollowUpAcknowledged());

        record.acknowledgeFollowUp();
        assertTrue(record.isFollowUpAcknowledged());
    }

    @Test
    @DisplayName("update() resets followUpAcknowledged to false even if it was already acknowledged")
    void updateResetsAcknowledgedFlag() {
        AgScoutingRecord record = newRecord(LocalDate.now().plusDays(7));
        record.acknowledgeFollowUp();
        assertTrue(record.isFollowUpAcknowledged());

        record.update("HIGH", "Aphid infestation spreading", "Spray immediately",
                LocalDate.now().plusDays(3), null);

        assertFalse(record.isFollowUpAcknowledged(),
                "changing the follow-up date via update() must re-arm the reminder");
        assertEquals("HIGH", record.getSeverity());
        assertEquals("Aphid infestation spreading", record.getDescription());
    }

    @Test
    @DisplayName("severity defaults to LOW when not supplied")
    void severityDefaultsToLow() {
        AgScoutingRecord record = AgScoutingRecord.create(TENANT, UUID.randomUUID(), LocalDate.now(),
                "GENERAL", null, "Routine check, nothing notable", null, null, null, null, null);
        assertEquals("LOW", record.getSeverity());
    }

    @Test
    @DisplayName("create() requires a non-blank description")
    void createRequiresDescription() {
        assertThrows(IllegalArgumentException.class, () -> AgScoutingRecord.create(
                TENANT, UUID.randomUUID(), LocalDate.now(), "PEST", "LOW", "", null,
                null, null, null, null));
    }
}
