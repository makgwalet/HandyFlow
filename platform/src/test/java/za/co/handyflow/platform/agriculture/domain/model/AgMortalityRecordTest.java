package za.co.handyflow.platform.agriculture.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AgMortalityRecordTest {

    private static final TenantId TENANT = TenantId.of(UUID.randomUUID());

    @Test
    @DisplayName("countLost must be exactly 1 when animalId is set")
    void animalMortalityRequiresCountOfOne() {
        UUID animalId = UUID.randomUUID();
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                AgMortalityRecord.create(TENANT, animalId, null, LocalDate.now(), 2,
                        "DISEASE", null, null, null, null, null));
        assertTrue(ex.getMessage().contains("countLost must be 1"));
    }

    @Test
    @DisplayName("countLost of 1 for an individually-tracked animal succeeds")
    void animalMortalityWithCountOneSucceeds() {
        UUID animalId = UUID.randomUUID();
        AgMortalityRecord record = AgMortalityRecord.create(TENANT, animalId, null, LocalDate.now(), 1,
                "PREDATOR", "Jackal", null, null, null, null);
        assertTrue(record.isForAnimal());
        assertEquals(1, record.getCountLost());
    }

    @Test
    @DisplayName("a group loss may record countLost greater than 1")
    void groupMortalityAllowsMultipleLosses() {
        UUID groupId = UUID.randomUUID();
        AgMortalityRecord record = AgMortalityRecord.create(TENANT, null, groupId, LocalDate.now(), 12,
                "DISEASE", "Newcastle outbreak", null, null, null, null);
        assertTrue(record.isForGroup());
        assertEquals(12, record.getCountLost());
    }

    @Test
    @DisplayName("countLost must be positive")
    void countLostMustBePositive() {
        UUID groupId = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () ->
                AgMortalityRecord.create(TENANT, null, groupId, LocalDate.now(), 0,
                        "UNKNOWN", null, null, null, null, null));
    }
}
