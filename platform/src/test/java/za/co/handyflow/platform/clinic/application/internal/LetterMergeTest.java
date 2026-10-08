package za.co.handyflow.platform.clinic.application.internal;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LetterMergeTest {

    @Test
    void fillsKnownFieldsWithOrWithoutSpaces() {
        Map<String, String> v = new HashMap<>();
        v.put("patient.name", "Liam Botha"); v.put("visit.date", "7 October 2026");
        assertEquals("Liam Botha was seen on 7 October 2026.", LetterMerge.render("{{patient.name}} was seen on {{ visit.date }}.", v));
    }

    @Test
    void aMissingValueBecomesADashSoTheGapShows() {
        Map<String, String> v = new HashMap<>();
        v.put("patient.dob", null); v.put("doctor.name", "  ");
        assertEquals("Born — by —.", LetterMerge.render("Born {{patient.dob}} by {{doctor.name}}.", v));
    }

    @Test
    void valuesWithDollarSignsAndBackslashesAreKeptAsTyped() {
        Map<String, String> v = Map.of("patient.name", "A$1\\B");
        assertEquals("A$1\\B", LetterMerge.render("{{patient.name}}", v));
    }

    @Test
    void unknownFieldsAreListedOnceInOrder() {
        assertEquals(List.of("patient.age", "x.y"), LetterMerge.unknown("{{patient.age}} {{patient.name}} {{x.y}} {{patient.age}}"));
        assertTrue(LetterMerge.unknown(null).isEmpty());
        assertTrue(LetterMerge.unknown("no fields { here }").isEmpty());
    }

    @Test
    void nullTextStaysNull() { assertNull(LetterMerge.render(null, Map.of())); }
}
