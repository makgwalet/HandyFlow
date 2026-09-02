package za.co.handyflow.platform.agriculture.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure entity-behavior tests — no Spring context, matching AgGroupTest's
 * own plain-JUnit convention for domain model classes.
 */
class AgCropCycleTest {

    private static final TenantId TENANT = TenantId.of(UUID.randomUUID());

    private AgCropCycle newCycle(LocalDate plantingDate) {
        return AgCropCycle.create(TENANT, UUID.randomUUID(), UUID.randomUUID(), null, null,
                UUID.randomUUID(), "Yellow Dent", "Field 3 - 2026 Summer", new BigDecimal("12.50"),
                plantingDate, LocalDate.now().plusMonths(4), null, null, null, null);
    }

    @Test
    @DisplayName("create() sets status PLANTED when a plantingDate is given, PLANNED otherwise")
    void createSetsStatusFromPlantingDate() {
        AgCropCycle planted = newCycle(LocalDate.now());
        assertEquals("PLANTED", planted.getStatus());

        AgCropCycle planned = newCycle(null);
        assertEquals("PLANNED", planned.getStatus());
    }

    @Test
    @DisplayName("recordPlanting() transitions PLANNED to PLANTED and sets seed fields")
    void recordPlantingTransitionsPlannedToPlanted() {
        AgCropCycle cycle = newCycle(null);
        UUID seedItem = UUID.randomUUID();

        cycle.recordPlanting(LocalDate.now(), seedItem, new BigDecimal("500"), "Local co-op");

        assertEquals("PLANTED", cycle.getStatus());
        assertEquals(seedItem, cycle.getSeedInventoryItemId());
        assertEquals(new BigDecimal("500"), cycle.getSeedQuantity());
        assertEquals("Local co-op", cycle.getSeedSource());
    }

    @Test
    @DisplayName("recordPlanting() on a cycle already PLANTED throws IllegalStateException")
    void recordPlantingOnAlreadyPlantedThrows() {
        AgCropCycle cycle = newCycle(LocalDate.now());
        assertEquals("PLANTED", cycle.getStatus());

        assertThrows(IllegalStateException.class,
                () -> cycle.recordPlanting(LocalDate.now(), null, null, null));
    }

    @Test
    @DisplayName("markGrowing() transitions PLANTED to GROWING; rejected from PLANNED")
    void markGrowingRequiresPlanted() {
        AgCropCycle planted = newCycle(LocalDate.now());
        planted.markGrowing();
        assertEquals("GROWING", planted.getStatus());

        AgCropCycle planned = newCycle(null);
        assertThrows(IllegalStateException.class, planned::markGrowing);
    }

    @Test
    @DisplayName("startHarvest() accepts PLANTED or GROWING; rejected from PLANNED")
    void startHarvestAcceptsPlantedOrGrowing() {
        AgCropCycle fromPlanted = newCycle(LocalDate.now());
        fromPlanted.startHarvest();
        assertEquals("HARVESTING", fromPlanted.getStatus());

        AgCropCycle fromGrowing = newCycle(LocalDate.now());
        fromGrowing.markGrowing();
        fromGrowing.startHarvest();
        assertEquals("HARVESTING", fromGrowing.getStatus());

        AgCropCycle fromPlanned = newCycle(null);
        assertThrows(IllegalStateException.class, fromPlanned::startHarvest);
    }

    @Test
    @DisplayName("completeHarvest() requires HARVESTING and transitions to HARVESTED")
    void completeHarvestRequiresHarvesting() {
        AgCropCycle cycle = newCycle(LocalDate.now());
        assertThrows(IllegalStateException.class, cycle::completeHarvest);

        cycle.startHarvest();
        cycle.completeHarvest();
        assertEquals("HARVESTED", cycle.getStatus());
    }

    @Test
    @DisplayName("markFailed() sets status FAILED from any state and appends the reason to notes")
    void markFailedSetsStatusAndAppendsReason() {
        AgCropCycle cycle = newCycle(LocalDate.now());
        cycle.markFailed("Hailstorm destroyed the crop");

        assertEquals("FAILED", cycle.getStatus());
        assertTrue(cycle.getNotes().contains("Hailstorm destroyed the crop"));
    }

    @Test
    @DisplayName("abandon() sets status ABANDONED and appends the reason to notes")
    void abandonSetsStatusAndAppendsReason() {
        AgCropCycle cycle = newCycle(null);
        cycle.abandon("Field reassigned to another cycle");

        assertEquals("ABANDONED", cycle.getStatus());
        assertTrue(cycle.getNotes().contains("Field reassigned to another cycle"));
    }

    @Test
    @DisplayName("create() requires a positive areaPlantedHectares")
    void createRequiresPositiveArea() {
        assertThrows(IllegalArgumentException.class, () -> AgCropCycle.create(
                TENANT, UUID.randomUUID(), UUID.randomUUID(), null, null, UUID.randomUUID(),
                null, null, BigDecimal.ZERO, null, null, null, null, null, null));
    }
}
