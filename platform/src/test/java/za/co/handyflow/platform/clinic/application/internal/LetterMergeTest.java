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
        assertEquals(List.of("patient.shoeSize", "x.y"), LetterMerge.unknown("{{patient.shoeSize}} {{patient.name}} {{x.y}} {{patient.shoeSize}}"));
        assertTrue(LetterMerge.unknown(null).isEmpty());
        assertTrue(LetterMerge.unknown("no fields { here }").isEmpty());
    }

    @Test
    void nullTextStaysNull() { assertNull(LetterMerge.render(null, Map.of())); }

    @Test
    void everyFieldHasAValueSlotAndVisitFieldsAreDashesWithoutAVisit() {
        var v = LetterMerge.values(new LetterMerge.Source(" Liam ", "Botha", java.time.LocalDate.of(2019, 3, 12), "1903125000087", "0821234567", "1 Main Rd, Pretoria",
                null, null, null, null, null, null, null, "Handy Practice", java.time.LocalDate.of(2026, 10, 8), "HR Manager", "Acme (Pty) Ltd"));
        for (String f : LetterMerge.FIELDS) assertTrue(v.containsKey(f), f);
        assertEquals("Liam Botha", v.get("patient.name"));
        assertEquals("7 years", v.get("patient.age"));
        assertEquals("Dear HR Manager at Acme (Pty) Ltd, — on —.", LetterMerge.render("Dear {{recipient.name}} at {{recipient.company}}, {{visit.diagnosis}} on {{visit.date}}.", v));
    }

    @Test
    void ageIsInMonthsUnderTwoYears() {
        var today = java.time.LocalDate.of(2026, 10, 8);
        assertEquals("7 months", LetterMerge.age(java.time.LocalDate.of(2026, 3, 1), today));
        assertEquals("1 month", LetterMerge.age(java.time.LocalDate.of(2026, 9, 1), today));
        assertEquals("2 years", LetterMerge.age(java.time.LocalDate.of(2024, 10, 8), today));
        assertNull(LetterMerge.age(java.time.LocalDate.of(2027, 1, 1), today));
        assertNull(LetterMerge.age(null, today));
    }
}
