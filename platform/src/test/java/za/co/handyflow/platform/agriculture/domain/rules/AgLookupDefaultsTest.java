package za.co.handyflow.platform.agriculture.domain.rules;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AgLookupDefaultsTest {

    @Test void speciesNamesAreUniqueAndUseKnownCategoriesAndModes() {
        Set<String> seen = new HashSet<>();
        for (var s : AgLookupDefaults.SPECIES) {
            assertTrue(seen.add(AgLookupDefaults.normalise(s.name())), "duplicate " + s.name());
            assertTrue(Set.of("LIVESTOCK", "POULTRY", "AQUACULTURE").contains(s.category()), s.name());
            assertTrue(Set.of("INDIVIDUAL", "GROUP", "BOTH").contains(s.trackingMode()), s.name());
            assertFalse(s.name().isBlank());
            if (s.gestationDays() != null) assertTrue(s.gestationDays() > 0 && s.gestationDays() < 500, s.name());
        }
        assertTrue(seen.containsAll(Set.of("cattle", "sheep", "goats", "pigs", "broilers", "layers")));
    }

    @Test void cropTypesAreUniqueAndUseKnownCategoriesAndUnits() {
        Set<String> seen = new HashSet<>();
        for (var c : AgLookupDefaults.CROP_TYPES) {
            assertTrue(seen.add(AgLookupDefaults.normalise(c.name())), "duplicate " + c.name());
            assertTrue(Set.of("CEREAL", "OILSEED", "LEGUME", "VEGETABLE", "FRUIT", "FODDER", "OTHER").contains(c.category()), c.name());
            assertTrue(Set.of("kg", "t", "bags", "bales", "crates").contains(c.unit()), c.name());
            if (c.typicalGrowingDays() != null) assertTrue(c.typicalGrowingDays() > 0 && c.typicalGrowingDays() <= 400, c.name());
        }
        assertTrue(seen.containsAll(Set.of("wheat", "soybeans", "sunflower", "potatoes")));
    }

    @Test void namesFoldCaseAndSpacing() {
        assertEquals("cattle", AgLookupDefaults.normalise("  CATTLE "));
        assertEquals("table grapes", AgLookupDefaults.normalise("Table   Grapes"));
        assertEquals("", AgLookupDefaults.normalise(null));
        assertEquals("Table Grapes", AgLookupDefaults.clean("  Table   Grapes "));
    }

    @Test void aDuplicateNameIsRefusedWhateverItsCase() {
        List<String> have = List.of("Cattle", "Sheep");
        AgLookupDefaults.requireNewName(have, "Goats", "species");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> AgLookupDefaults.requireNewName(have, " cattle ", "species"));
        assertEquals("A species named 'Cattle' already exists", e.getMessage());
        assertThrows(IllegalArgumentException.class, () -> AgLookupDefaults.requireNewName(have, "SHEEP", "species"));
    }

    @Test void anEmptyTenantGetsTheWholeList() {
        assertEquals(AgLookupDefaults.SPECIES.size(), AgLookupDefaults.missingSpecies(List.of()).size());
        assertEquals(AgLookupDefaults.CROP_TYPES.size(), AgLookupDefaults.missingCropTypes(List.of()).size());
    }

    @Test void whatTheTenantAlreadyHasIsNotAddedAgain() {
        var missing = AgLookupDefaults.missingSpecies(List.of("cattle", "SHEEP ", "My own breed group"));
        assertEquals(AgLookupDefaults.SPECIES.size() - 2, missing.size());
        assertTrue(missing.stream().noneMatch(s -> s.name().equals("Cattle") || s.name().equals("Sheep")));
        assertTrue(AgLookupDefaults.missingCropTypes(List.of("Wheat")).stream().noneMatch(c -> c.name().equals("Wheat")));
    }

    @Test void addingTwiceAddsNothingTheSecondTime() {
        List<String> have = AgLookupDefaults.SPECIES.stream().map(AgLookupDefaults.SpeciesDefault::name).toList();
        assertTrue(AgLookupDefaults.missingSpecies(have).isEmpty());
        assertTrue(AgLookupDefaults.missingCropTypes(AgLookupDefaults.CROP_TYPES.stream().map(AgLookupDefaults.CropTypeDefault::name).toList()).isEmpty());
    }
}
