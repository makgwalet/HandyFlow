package za.co.handyflow.platform.agriculture.domain.rules;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AgAuditRulesTest {

    @Test void knownTypesAreAcceptedInAnyCaseAndSpacing() {
        assertEquals("animals", AgAuditRules.requireType(" Animals "));
        for (String t : AgAuditRules.TYPES) assertEquals(t, AgAuditRules.requireType(t.toUpperCase()));
        assertEquals(13, AgAuditRules.TYPES.size());
        for (String t : List.of("weights", "mortality", "movements", "feed-records", "scouting", "harvests", "input-applications")) assertEquals(t, AgAuditRules.requireType(t));
    }

    @Test void anUnknownTypeIsRefusedAndTheMessageListsTheRest() {
        var e = assertThrows(IllegalArgumentException.class, () -> AgAuditRules.requireType("tractors"));
        assertTrue(e.getMessage().contains("'tractors'"));
        assertTrue(e.getMessage().contains("crop-cycles"));
        assertThrows(IllegalArgumentException.class, () -> AgAuditRules.requireType(null));
        assertThrows(IllegalArgumentException.class, () -> AgAuditRules.requireType(""));
    }

    @Test void aRealNameIsKept() {
        assertEquals("Thandi Nkosi", AgAuditRules.cleanName(UUID.randomUUID(), "  Thandi Nkosi "));
    }

    @Test void noNameMeansNoName() {
        assertNull(AgAuditRules.cleanName(UUID.randomUUID(), null));
        assertNull(AgAuditRules.cleanName(UUID.randomUUID(), "   "));
    }

    @Test void unknownWithNoUserIsNobody() {
        assertNull(AgAuditRules.cleanName(null, "Unknown"));
    }

    @Test void aPersonCalledUnknownWithAnIdIsKept() {
        assertEquals("Unknown", AgAuditRules.cleanName(UUID.randomUUID(), "Unknown"));
    }

    @Test void longNamesAreCutToTheColumnWidth() {
        assertEquals(255, AgAuditRules.cleanName(UUID.randomUUID(), "x".repeat(300)).length());
        assertEquals(255, AgAuditRules.cleanName(UUID.randomUUID(), "x".repeat(255)).length());
    }

    @Test void aBatchNeedsAtLeastOneIdAndIgnoresRepeatsAndNulls() {
        UUID a = new UUID(0, 1), b = new UUID(0, 2);
        assertEquals(List.of(a, b), AgAuditRules.requireIds(Arrays.asList(a, null, b, a)));
        assertThrows(IllegalArgumentException.class, () -> AgAuditRules.requireIds(List.of()));
        assertThrows(IllegalArgumentException.class, () -> AgAuditRules.requireIds(null));
        assertThrows(IllegalArgumentException.class, () -> AgAuditRules.requireIds(Arrays.asList((UUID) null)));
    }

    @Test void aBatchIsCappedAtTwoHundred() {
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 201; i++) ids.add(new UUID(0, i + 1));
        var e = assertThrows(IllegalArgumentException.class, () -> AgAuditRules.requireIds(ids));
        assertTrue(e.getMessage().contains("200"));
        assertEquals(200, AgAuditRules.requireIds(ids.subList(0, 200)).size());
        assertEquals(200, AgAuditRules.MAX_BATCH);
    }
}
