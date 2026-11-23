package za.co.handyflow.platform.agriculture.domain.rules;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AgSuggestionRulesTest {

    private static final UUID COW = new UUID(0, 1), SHEEP = new UUID(0, 2);

    @Test void tidyTrimsDedupesIgnoringCaseAndSorts() {
        assertEquals(List.of("Angus", "nguni", "zebu cross"), AgSuggestionRules.tidy(Arrays.asList("nguni", " Nguni ", "NGUNI", "Angus", "zebu cross")));
    }

    @Test void tidyKeepsTheFirstSpellingSeen() {
        assertEquals(List.of("nguni"), AgSuggestionRules.tidy(List.of("nguni", "Nguni")));
    }

    @Test void tidyDropsBlanksNullsAndOverLongValues() {
        assertEquals(List.of("A"), AgSuggestionRules.tidy(Arrays.asList(null, "", "   ", "A", "x".repeat(101))));
        assertEquals(1, AgSuggestionRules.tidy(List.of("x".repeat(100))).size());
        assertEquals(List.of(), AgSuggestionRules.tidy(null));
    }

    @Test void tidyStopsAtTheLimit() {
        List<String> many = new ArrayList<>();
        for (int i = 0; i < 250; i++) many.add(String.format("Breed %03d", i));
        List<String> out = AgSuggestionRules.tidy(many);
        assertEquals(200, out.size());
        assertEquals("Breed 000", out.get(0));
        assertEquals("Breed 199", out.get(199));
    }

    @Test void exactlyOneOverTheLimitIsCutToTheLimit() {
        List<String> many = new ArrayList<>();
        for (int i = 0; i < 201; i++) many.add(String.format("Breed %03d", i));
        assertEquals(200, AgSuggestionRules.tidy(many).size());
        assertEquals(200, AgSuggestionRules.tidy(many.subList(0, 200)).size());
    }

    @Test void sortingIgnoresCase() {
        assertEquals(List.of("apple", "Banana", "cherry"), AgSuggestionRules.tidy(List.of("cherry", "Banana", "apple")));
    }

    @Test void breedsAreGroupedBySpeciesAndTidied() {
        Map<UUID, List<String>> out = AgSuggestionRules.breedsBySpecies(List.of(
                new Object[]{COW, "Nguni"}, new Object[]{COW, "nguni"}, new Object[]{COW, "Angus"}, new Object[]{SHEEP, "Dorper"}));
        assertEquals(List.of("Angus", "Nguni"), out.get(COW));
        assertEquals(List.of("Dorper"), out.get(SHEEP));
        assertEquals(2, out.size());
    }

    @Test void badRowsAreIgnoredAndASpeciesWithNothingLeftIsOmitted() {
        Map<UUID, List<String>> out = AgSuggestionRules.breedsBySpecies(Arrays.asList(
                null, new Object[]{COW}, new Object[]{null, "X"}, new Object[]{COW, null}, new Object[]{SHEEP, "  "}, new Object[]{"not-a-uuid", "Y"}, new Object[]{COW, "Brahman"}));
        assertEquals(Map.of(COW, List.of("Brahman")), out);
        assertTrue(AgSuggestionRules.breedsBySpecies(null).isEmpty());
    }
}
