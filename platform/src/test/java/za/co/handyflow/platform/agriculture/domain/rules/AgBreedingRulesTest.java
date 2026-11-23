package za.co.handyflow.platform.agriculture.domain.rules;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AgBreedingRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 23);
    private static final LocalDate MATED = LocalDate.of(2026, 3, 1);

    @Test void pendingCanGoToEveryOutcome() {
        for (String to : List.of("CONFIRMED_PREGNANT", "NOT_PREGNANT", "BIRTHED", "STILLBORN", "ABORTED", "FAILED"))
            AgBreedingRules.requireTransition("PENDING", to);
    }

    @Test void confirmedCanOnlyEndInBirthAbortionOrFailure() {
        for (String to : List.of("BIRTHED", "STILLBORN", "ABORTED", "FAILED")) AgBreedingRules.requireTransition("CONFIRMED_PREGNANT", to);
        assertThrows(IllegalStateException.class, () -> AgBreedingRules.requireTransition("CONFIRMED_PREGNANT", "NOT_PREGNANT"));
        assertThrows(IllegalStateException.class, () -> AgBreedingRules.requireTransition("CONFIRMED_PREGNANT", "CONFIRMED_PREGNANT"));
    }

    @Test void finalOutcomesCannotChange() {
        for (String from : List.of("NOT_PREGNANT", "BIRTHED", "STILLBORN", "ABORTED", "FAILED"))
            for (String to : List.of("PENDING", "CONFIRMED_PREGNANT", "NOT_PREGNANT", "BIRTHED", "STILLBORN", "ABORTED", "FAILED"))
                assertThrows(IllegalStateException.class, () -> AgBreedingRules.requireTransition(from, to), from + "->" + to);
    }

    @Test void nothingGoesBackToPending() {
        assertThrows(IllegalStateException.class, () -> AgBreedingRules.requireTransition("CONFIRMED_PREGNANT", "PENDING"));
    }

    @Test void transitionMessageNamesBothStates() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> AgBreedingRules.requireTransition("FAILED", "BIRTHED"));
        assertEquals("A breeding record that is failed cannot be changed to birthed", e.getMessage());
    }

    @Test void typesAreNormalisedAndChecked() {
        assertEquals("AI", AgBreedingRules.normaliseType("ARTIFICIAL_INSEMINATION"));
        assertEquals("AI", AgBreedingRules.normaliseType(" ai "));
        assertEquals("NATURAL", AgBreedingRules.normaliseType("natural"));
        assertEquals("EMBRYO_TRANSFER", AgBreedingRules.normaliseType("EMBRYO_TRANSFER"));
        assertThrows(IllegalArgumentException.class, () -> AgBreedingRules.normaliseType("WEIRD"));
        assertThrows(IllegalArgumentException.class, () -> AgBreedingRules.normaliseType(" "));
        assertThrows(IllegalArgumentException.class, () -> AgBreedingRules.normaliseType(null));
    }

    @Test void matingDates() {
        AgBreedingRules.requireMatingDates(MATED, null, TODAY);
        AgBreedingRules.requireMatingDates(TODAY, TODAY, TODAY);
        assertThrows(IllegalArgumentException.class, () -> AgBreedingRules.requireMatingDates(TODAY.plusDays(1), null, TODAY));
        assertThrows(IllegalArgumentException.class, () -> AgBreedingRules.requireMatingDates(MATED, MATED.minusDays(1), TODAY));
        assertThrows(IllegalArgumentException.class, () -> AgBreedingRules.requireMatingDates(null, null, TODAY));
    }

    @Test void birthDates() {
        AgBreedingRules.requireBirthDate(MATED, MATED, TODAY);
        AgBreedingRules.requireBirthDate(MATED, TODAY, TODAY);
        assertThrows(IllegalArgumentException.class, () -> AgBreedingRules.requireBirthDate(MATED, MATED.minusDays(1), TODAY));
        assertThrows(IllegalArgumentException.class, () -> AgBreedingRules.requireBirthDate(MATED, TODAY.plusDays(1), TODAY));
        assertThrows(IllegalArgumentException.class, () -> AgBreedingRules.requireBirthDate(MATED, null, TODAY));
    }

    @Test void birthCounts() {
        assertEquals(2, AgBreedingRules.requireBirthCounts(2, null)[0]);
        assertEquals(1, AgBreedingRules.requireBirthCounts(null, 1)[1]);
        assertEquals(0, AgBreedingRules.requireBirthCounts(null, 1)[0]);
        assertThrows(IllegalArgumentException.class, () -> AgBreedingRules.requireBirthCounts(null, null));
        assertThrows(IllegalArgumentException.class, () -> AgBreedingRules.requireBirthCounts(0, 0));
        assertThrows(IllegalArgumentException.class, () -> AgBreedingRules.requireBirthCounts(-1, 2));
        assertThrows(IllegalArgumentException.class, () -> AgBreedingRules.requireBirthCounts(101, 0));
        AgBreedingRules.requireBirthCounts(100, 0);
    }

    @Test void outcomeFollowsLiveCount() {
        assertEquals("BIRTHED", AgBreedingRules.outcomeForBirth(1));
        assertEquals("BIRTHED", AgBreedingRules.outcomeForBirth(5));
        assertEquals("STILLBORN", AgBreedingRules.outcomeForBirth(0));
    }

    @Test void damMustBeALiveFemale() {
        AgBreedingRules.requireDam("FEMALE", "ACTIVE");
        assertThrows(IllegalArgumentException.class, () -> AgBreedingRules.requireDam("MALE", "ACTIVE"));
        assertThrows(IllegalArgumentException.class, () -> AgBreedingRules.requireDam("CASTRATED", "ACTIVE"));
        assertThrows(IllegalArgumentException.class, () -> AgBreedingRules.requireDam(null, "ACTIVE"));
        for (String st : List.of("SOLD", "DECEASED", "CULLED", "TRANSFERRED_OUT"))
            assertThrows(IllegalArgumentException.class, () -> AgBreedingRules.requireDam("FEMALE", st));
    }

    @Test void sireMustBeALiveEntireMale() {
        AgBreedingRules.requireSire("MALE", "ACTIVE");
        assertThrows(IllegalArgumentException.class, () -> AgBreedingRules.requireSire("FEMALE", "ACTIVE"));
        assertThrows(IllegalArgumentException.class, () -> AgBreedingRules.requireSire("CASTRATED", "ACTIVE"));
        assertThrows(IllegalArgumentException.class, () -> AgBreedingRules.requireSire("MALE", "DECEASED"));
    }

    @Test void finalOutcomesHaveNoNext() {
        assertEquals(Set.of(), AgBreedingRules.allowedNext("BIRTHED"));
        assertEquals(Set.of(), AgBreedingRules.allowedNext(null));
    }
}
