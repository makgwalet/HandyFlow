package za.co.handyflow.platform.agriculture.domain.rules;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AgReferenceRulesTest {

    private static final UUID SELF = UUID.randomUUID(), PARENT = UUID.randomUUID(), SPECIES = UUID.randomUUID();
    private static final LocalDate PARENT_BORN = LocalDate.of(2020, 1, 1), CHILD_BORN = LocalDate.of(2024, 5, 1);

    @Test void anInactiveFarmTakesNothingNew() {
        AgReferenceRules.requireFarmUsable("ACTIVE", "Home");
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> AgReferenceRules.requireFarmUsable("INACTIVE", "Home"));
        assertEquals("Farm Home is inactive. Reactivate it before adding to it", e.getMessage());
    }

    @Test void somethingFromAnotherFarmIsRefused() {
        UUID farm = UUID.randomUUID();
        AgReferenceRules.requireOnFarm("enterprise Dairy", farm, farm);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> AgReferenceRules.requireOnFarm("enterprise Dairy", UUID.randomUUID(), farm));
        assertEquals("enterprise Dairy does not belong to this farm", e.getMessage());
        assertThrows(IllegalArgumentException.class, () -> AgReferenceRules.requireOnFarm("x", null, farm));
    }

    @Test void noParentIsFine() {
        AgReferenceRules.requireParent("sire", null, SELF, null, null, SPECIES, null, null);
    }

    @Test void aGoodParentPasses() {
        AgReferenceRules.requireParent("sire", PARENT, SELF, "MALE", SPECIES, SPECIES, PARENT_BORN, CHILD_BORN);
        AgReferenceRules.requireParent("dam", PARENT, SELF, "FEMALE", SPECIES, SPECIES, PARENT_BORN, CHILD_BORN);
    }

    @Test void anAnimalIsNotItsOwnParent() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> AgReferenceRules.requireParent("dam", SELF, SELF, "FEMALE", SPECIES, SPECIES, null, null));
        assertEquals("An animal cannot be its own dam", e.getMessage());
    }

    @Test void sireIsMaleAndDamIsFemale() {
        for (String sex : new String[] {"FEMALE", "CASTRATED", null})
            assertThrows(IllegalArgumentException.class, () -> AgReferenceRules.requireParent("sire", PARENT, SELF, sex, SPECIES, SPECIES, null, null), "sire " + sex);
        for (String sex : new String[] {"MALE", "CASTRATED", null})
            assertThrows(IllegalArgumentException.class, () -> AgReferenceRules.requireParent("dam", PARENT, SELF, sex, SPECIES, SPECIES, null, null), "dam " + sex);
    }

    @Test void parentIsTheSameSpecies() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> AgReferenceRules.requireParent("sire", PARENT, SELF, "MALE", UUID.randomUUID(), SPECIES, null, null));
        assertEquals("The sire is a different species", e.getMessage());
    }

    @Test void parentIsBornBeforeTheAnimal() {
        assertThrows(IllegalArgumentException.class, () -> AgReferenceRules.requireParent("sire", PARENT, SELF, "MALE", SPECIES, SPECIES, CHILD_BORN, CHILD_BORN));
        assertThrows(IllegalArgumentException.class, () -> AgReferenceRules.requireParent("sire", PARENT, SELF, "MALE", SPECIES, SPECIES, CHILD_BORN.plusDays(1), CHILD_BORN));
        AgReferenceRules.requireParent("sire", PARENT, SELF, "MALE", SPECIES, SPECIES, null, CHILD_BORN);   // unknown birth date is not held against the parent
        AgReferenceRules.requireParent("sire", PARENT, SELF, "MALE", SPECIES, SPECIES, PARENT_BORN, null);
    }

    @Test void sireAndDamAreDifferentAnimals() {
        AgReferenceRules.requireDifferentParents(null, null);
        AgReferenceRules.requireDifferentParents(PARENT, null);
        AgReferenceRules.requireDifferentParents(PARENT, UUID.randomUUID());
        assertThrows(IllegalArgumentException.class, () -> AgReferenceRules.requireDifferentParents(PARENT, PARENT));
    }

    @Test void holdingsAreListedInWords() {
        assertNull(AgReferenceRules.farmHoldings(0, 0, 0, 0));
        assertEquals("1 animal on the farm", AgReferenceRules.farmHoldings(1, 0, 0, 0));
        assertEquals("12 animals on the farm and 2 active groups", AgReferenceRules.farmHoldings(12, 2, 0, 0));
        assertEquals("12 animals on the farm, 1 active group, 3 crop cycles in progress and 1 stock item with stock on hand", AgReferenceRules.farmHoldings(12, 1, 3, 1));
    }

    @Test void aFarmWithLiveHoldingsCannotBeSwitchedOff() {
        AgReferenceRules.requireNoHoldings("Home", "deleted", 0, 0, 0, 0);
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> AgReferenceRules.requireNoHoldings("Home", "deactivated", 0, 1, 0, 2));
        assertEquals("Farm Home cannot be deactivated: it still has 1 active group and 2 stock items with stock on hand. Sell, close, harvest or move them first", e.getMessage());
        for (long[] c : new long[][] {{1, 0, 0, 0}, {0, 1, 0, 0}, {0, 0, 1, 0}, {0, 0, 0, 1}})
            assertThrows(IllegalStateException.class, () -> AgReferenceRules.requireNoHoldings("Home", "deleted", c[0], c[1], c[2], c[3]));
    }
}
