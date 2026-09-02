package za.co.handyflow.platform.agriculture.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exercises AgTrackingTarget.requireExactlyOne() indirectly via
 * AgWeightRecord.create() — one representative history entity is enough to
 * prove the shared invariant, since every one of the six history entities
 * (AgWeightRecord, AgHealthEvent, AgBreedingRecord, AgMovementRecord,
 * AgMortalityRecord, AgFeedRecord) delegates to the exact same static check.
 */
class AgTrackingTargetTest {

    private static final TenantId TENANT = TenantId.of(UUID.randomUUID());

    @Test
    @DisplayName("create() with neither animalId nor groupId throws IllegalArgumentException")
    void neitherTargetThrows() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                AgWeightRecord.create(TENANT, null, null, LocalDate.now(), BigDecimal.TEN, null, null, null, null));
        assertTrue(ex.getMessage().contains("exactly one"));
    }

    @Test
    @DisplayName("create() with BOTH animalId and groupId throws IllegalArgumentException")
    void bothTargetsThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                AgWeightRecord.create(TENANT, UUID.randomUUID(), UUID.randomUUID(),
                        LocalDate.now(), BigDecimal.TEN, null, null, null, null));
    }

    @Test
    @DisplayName("create() with only animalId succeeds and isForAnimal()/isForGroup() reflect it")
    void animalOnlySucceeds() {
        AgWeightRecord record = AgWeightRecord.create(TENANT, UUID.randomUUID(), null,
                LocalDate.now(), BigDecimal.TEN, null, null, null, null);
        assertTrue(record.isForAnimal());
        assertFalse(record.isForGroup());
    }

    @Test
    @DisplayName("create() with only groupId succeeds and isForAnimal()/isForGroup() reflect it")
    void groupOnlySucceeds() {
        AgWeightRecord record = AgWeightRecord.create(TENANT, null, UUID.randomUUID(),
                LocalDate.now(), BigDecimal.TEN, 25, null, null, null);
        assertFalse(record.isForAnimal());
        assertTrue(record.isForGroup());
    }
}
