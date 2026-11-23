package za.co.handyflow.platform.agriculture.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.HandyFlowException;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AgPatchRulesTest {

    private static final Set<String> NONE = Set.of();

    @Test void leftOutFieldKeepsItsValue() {
        UUID sire = UUID.randomUUID();
        assertEquals(sire, AgPatchRules.apply("sireId", sire, null, NONE));
        LocalDate dob = LocalDate.of(2024, 3, 1);
        assertEquals(dob, AgPatchRules.apply("dateOfBirth", dob, null, NONE));
    }

    @Test void givenValueReplaces() {
        assertEquals("Boer", AgPatchRules.apply("breed", "Dorper", "Boer", NONE));
    }

    @Test void textIsTrimmed() {
        assertEquals("Boer", AgPatchRules.apply("breed", "Dorper", "  Boer ", NONE));
    }

    @Test void blankTextKeepsValue() {
        assertEquals("Dorper", AgPatchRules.apply("breed", "Dorper", "   ", NONE));
    }

    @Test void namedFieldIsEmptied() {
        UUID dam = UUID.randomUUID();
        assertNull(AgPatchRules.apply("damId", dam, null, Set.of("damId")));
        assertNull(AgPatchRules.apply("notes", "x", "", Set.of("notes")));
    }

    @Test void valueAndClearTogetherIsRefused() {
        HandyFlowException e = assertThrows(HandyFlowException.class,
                () -> AgPatchRules.apply("notes", "x", "new", Set.of("notes")));
        assertEquals("CONFLICTING_FIELD", e.getErrorCode());
    }

    @Test void clearListChecksNames() {
        assertEquals(Set.of("notes", "damId"), AgPatchRules.clearSet(List.of("notes", "damId"), AgPatchRules.ANIMAL_CLEARABLE));
        assertTrue(AgPatchRules.clearSet(null, AgPatchRules.ANIMAL_CLEARABLE).isEmpty());
        HandyFlowException e = assertThrows(HandyFlowException.class,
                () -> AgPatchRules.clearSet(List.of("tagNumber"), AgPatchRules.ANIMAL_CLEARABLE));
        assertEquals("INVALID_CLEAR_FIELD", e.getErrorCode());
        assertThrows(HandyFlowException.class, () -> AgPatchRules.clearSet(List.of("name"), AgPatchRules.GROUP_CLEARABLE));
    }

    @Test void identityFieldsAreNotClearable() {
        for (String f : List.of("tagNumber", "farmId", "speciesId", "status", "batchNumber", "currentCount"))
            assertFalse(AgPatchRules.ANIMAL_CLEARABLE.contains(f) || AgPatchRules.GROUP_CLEARABLE.contains(f), f);
    }
}
