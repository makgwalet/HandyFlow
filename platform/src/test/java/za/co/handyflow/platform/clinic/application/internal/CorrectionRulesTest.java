package za.co.handyflow.platform.clinic.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.clinic.application.internal.CorrectionRules.Change;
import za.co.handyflow.platform.clinic.application.internal.CorrectionRules.Facts;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CorrectionRulesTest {

    private static final Facts BEFORE = new Facts("Liam", "Botha", "2019015026088", LocalDate.of(2019, 1, 15), "MALE", "MALE");

    @Test
    void nothingChangedIsNoRows() {
        assertTrue(CorrectionRules.diff(BEFORE, BEFORE).isEmpty());
    }

    @Test
    void eachChangedFieldIsOneRowWithTheOldAndNewValue() {
        var after = new Facts("Liam", "Bothma", "2019015026088", LocalDate.of(2019, 1, 16), "MALE", "MALE");
        assertEquals(List.of(new Change("LAST_NAME", "Botha", "Bothma"), new Change("DATE_OF_BIRTH", "2019-01-15", "2019-01-16")),
                CorrectionRules.diff(BEFORE, after));
    }

    @Test
    void blankAndMissingAreTheSameThing() {
        var withoutId = new Facts("Liam", "Botha", null, null, null, null);
        var blankId = new Facts("Liam", "Botha", "  ", null, "", null);
        assertTrue(CorrectionRules.diff(withoutId, blankId).isEmpty());
    }

    @Test
    void addingAnIdForTheFirstTimeIsARowWithNoOldValue() {
        var before = new Facts("Liam", "Botha", null, null, null, null);
        var after = new Facts("Liam", "Botha", "A01234567", null, null, null);
        assertEquals(List.of(new Change("ID_NUMBER", null, "A01234567")), CorrectionRules.diff(before, after));
    }

    @Test
    void spacesAroundAValueAreNotAChange() {
        var after = new Facts(" Liam ", "Botha", "2019015026088", LocalDate.of(2019, 1, 15), "MALE", "MALE");
        assertTrue(CorrectionRules.diff(BEFORE, after).isEmpty());
    }
}
