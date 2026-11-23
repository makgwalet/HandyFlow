package za.co.handyflow.platform.agriculture.domain.rules;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AgLifecycleRulesTest {

    @Test void anActiveAnimalCanLeaveInAnyOfFourWays() {
        for (String to : List.of("SOLD", "DECEASED", "CULLED", "TRANSFERRED_OUT")) AgLifecycleRules.requireAnimalStatusChange("ACTIVE", to);
    }

    @Test void soldCulledAndTransferredAnimalsCanOnlyBePutBackToActive() {
        for (String from : List.of("SOLD", "CULLED", "TRANSFERRED_OUT")) {
            AgLifecycleRules.requireAnimalStatusChange(from, "ACTIVE");
            for (String to : List.of("SOLD", "DECEASED", "CULLED", "TRANSFERRED_OUT"))
                if (!to.equals(from)) assertThrows(IllegalStateException.class, () -> AgLifecycleRules.requireAnimalStatusChange(from, to), from + "->" + to);
        }
    }

    @Test void aDeceasedAnimalStaysDeceased() {
        for (String to : List.of("ACTIVE", "SOLD", "CULLED", "TRANSFERRED_OUT"))
            assertThrows(IllegalStateException.class, () -> AgLifecycleRules.requireAnimalStatusChange("DECEASED", to));
    }

    @Test void changingToTheSameStatusIsRefused() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> AgLifecycleRules.requireAnimalStatusChange("ACTIVE", "ACTIVE"));
        assertEquals("This animal is already active", e.getMessage());
    }

    @Test void unknownStatusIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> AgLifecycleRules.requireAnimalStatusChange("ACTIVE", "MISSING"));
        assertThrows(IllegalArgumentException.class, () -> AgLifecycleRules.requireAnimalStatusChange("ACTIVE", null));
    }

    @Test void allowedStatusesMatchTheRules() {
        assertEquals(Set.of("SOLD", "DECEASED", "CULLED", "TRANSFERRED_OUT"), AgLifecycleRules.allowedAnimalStatuses("ACTIVE"));
        assertEquals(Set.of("ACTIVE"), AgLifecycleRules.allowedAnimalStatuses("SOLD"));
        assertEquals(Set.of(), AgLifecycleRules.allowedAnimalStatuses("DECEASED"));
        assertEquals(Set.of(), AgLifecycleRules.allowedAnimalStatuses(null));
    }

    @Test void onlyThingsOnTheFarmCanBeChanged() {
        AgLifecycleRules.requireOnFarm("ACTIVE", "This animal");
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> AgLifecycleRules.requireOnFarm("SOLD", "This animal"));
        assertEquals("This animal is sold and can no longer be changed this way", e.getMessage());
        assertThrows(IllegalStateException.class, () -> AgLifecycleRules.requireOnFarm("CLOSED", "This group"));
        assertThrows(IllegalStateException.class, () -> AgLifecycleRules.requireOnFarm(null, "This group"));
    }

    @Test void aReasonNeedsThreeCharacters() {
        assertEquals("Sold to Van der Merwe", AgLifecycleRules.requireReason("  Sold to Van der Merwe "));
        assertEquals("abc", AgLifecycleRules.requireReason("abc"));
        assertThrows(IllegalArgumentException.class, () -> AgLifecycleRules.requireReason("ab"));
        assertThrows(IllegalArgumentException.class, () -> AgLifecycleRules.requireReason("   "));
        assertThrows(IllegalArgumentException.class, () -> AgLifecycleRules.requireReason(null));
    }

    @Test void movementTypeDecidesTheAnimalsNewStatus() {
        assertEquals("SOLD", AgLifecycleRules.animalStatusForMovement("SALE"));
        assertEquals("TRANSFERRED_OUT", AgLifecycleRules.animalStatusForMovement("TRANSFER_OUT"));
        assertNull(AgLifecycleRules.animalStatusForMovement("AREA_TRANSFER"));
        assertNull(AgLifecycleRules.animalStatusForMovement("FARM_TRANSFER"));
    }

    @Test void headsLeavingDefaultsToTheWholeGroup() {
        assertEquals(50, AgLifecycleRules.headsLeaving(null, 50));
        assertEquals(20, AgLifecycleRules.headsLeaving(20, 50));
        assertEquals(50, AgLifecycleRules.headsLeaving(50, 50));
        assertThrows(IllegalArgumentException.class, () -> AgLifecycleRules.headsLeaving(0, 50));
        assertThrows(IllegalArgumentException.class, () -> AgLifecycleRules.headsLeaving(51, 50));
        assertThrows(IllegalArgumentException.class, () -> AgLifecycleRules.headsLeaving(null, 0));
    }

    @Test void onlyAWholeGroupChangesFarm() {
        AgLifecycleRules.requireWholeGroupForFarmTransfer(null, 50);
        AgLifecycleRules.requireWholeGroupForFarmTransfer(50, 50);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> AgLifecycleRules.requireWholeGroupForFarmTransfer(20, 50));
        assertTrue(e.getMessage().contains("Only a whole group"));
    }

    @Test void farmTransferNeedsADifferentFarm() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        AgLifecycleRules.requireDifferentFarm(a, b);
        assertThrows(IllegalArgumentException.class, () -> AgLifecycleRules.requireDifferentFarm(a, a));
        assertThrows(IllegalArgumentException.class, () -> AgLifecycleRules.requireDifferentFarm(a, null));
    }

    @Test void mortalityCounts() {
        AgLifecycleRules.requireMortalityCount(true, 1, 0);
        AgLifecycleRules.requireMortalityCount(false, 5, 5);
        assertThrows(IllegalArgumentException.class, () -> AgLifecycleRules.requireMortalityCount(true, 2, 0));
        assertThrows(IllegalArgumentException.class, () -> AgLifecycleRules.requireMortalityCount(true, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> AgLifecycleRules.requireMortalityCount(false, 0, 5));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> AgLifecycleRules.requireMortalityCount(false, 6, 5));
        assertEquals("Cannot record 6 lost: the group has 5", e.getMessage());
    }

    @Test void datesCannotBeInTheFuture() {
        LocalDate today = LocalDate.of(2026, 11, 23);
        AgLifecycleRules.requireNotFuture(today, today, "The date");
        AgLifecycleRules.requireNotFuture(null, today, "The date");
        assertThrows(IllegalArgumentException.class, () -> AgLifecycleRules.requireNotFuture(today.plusDays(1), today, "The date"));
    }
}
