package za.co.handyflow.platform.clinic.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.clinic.dto.VisitMappingDtos.Entry;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VisitMappingRulesTest {

    @Test void visitTypeIsUpperCasedAndOddValuesAreRefused() {
        assertEquals("FOLLOW_UP", VisitMappingRules.visitType(" follow_up "));
        assertThrows(IllegalArgumentException.class, () -> VisitMappingRules.visitType("a b"));
        assertThrows(IllegalArgumentException.class, () -> VisitMappingRules.visitType(null));
    }

    @Test void keepsOrderAndTrimsCodes() {
        List<Entry> out = VisitMappingRules.check(List.of(new Entry(" B ", true), new Entry("A", false)));
        assertEquals(List.of("B", "A"), out.stream().map(Entry::groupCode).toList());
        assertTrue(out.get(0).required());
    }

    @Test void refusesDuplicatesBadCodesAndNull() {
        assertThrows(IllegalArgumentException.class, () -> VisitMappingRules.check(List.of(new Entry("A", false), new Entry("A", true))));
        assertThrows(IllegalArgumentException.class, () -> VisitMappingRules.check(List.of(new Entry("1bad", false))));
        assertThrows(IllegalArgumentException.class, () -> VisitMappingRules.check(java.util.Arrays.asList((Entry) null)));
        assertThrows(IllegalArgumentException.class, () -> VisitMappingRules.check(null));
    }

    @Test void anEmptyListIsAllowedAndTooManyIsNot() {
        assertTrue(VisitMappingRules.check(List.of()).isEmpty());
        List<Entry> many = new ArrayList<>();
        for (int i = 0; i <= VisitMappingRules.MAX_GROUPS; i++) many.add(new Entry("G" + i, false));
        assertThrows(IllegalArgumentException.class, () -> VisitMappingRules.check(many));
    }
}
