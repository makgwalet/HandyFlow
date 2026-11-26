package za.co.handyflow.platform.trainingprovider.domain.rules;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProgrammeNeedsRulesTest {

    private static final String NOTE = "Listed in the programme's own curriculum";

    @Test
    void creationChecks() {
        assertNull(ProgrammeNeedsRules.problem("EQUIPMENT", "Arc welding machine", null, 2, null, NOTE));
        assertEquals("Choose the type of resource", ProgrammeNeedsRules.problem("BOGUS", "Arc welding machine", null, 2, null, NOTE));
        assertEquals("Say what is needed", ProgrammeNeedsRules.problem("EQUIPMENT", "A", null, 2, null, NOTE));
        assertEquals("The quantity must be at least 1", ProgrammeNeedsRules.problem("EQUIPMENT", "Arc welding machine", null, 0, null, NOTE));
        assertEquals("Learners per set must be at least 1", ProgrammeNeedsRules.problem("EQUIPMENT", "Arc welding machine", null, 1, 0, NOTE));
        assertTrue(ProgrammeNeedsRules.problem("EQUIPMENT", "Arc welding machine", "w", 1, null, NOTE).contains("at least two"));
        assertTrue(ProgrammeNeedsRules.problem("EQUIPMENT", "Arc welding machine", null, 1, null, "short").contains("at least 10"));
    }

    @Test
    void requiredScalesByStartedGroupsOfLearners() {
        assertEquals(2, ProgrammeNeedsRules.required(2, null, 30));
        assertEquals(2, ProgrammeNeedsRules.required(2, 4, 0));
        assertEquals(2, ProgrammeNeedsRules.required(2, 4, 4));
        assertEquals(4, ProgrammeNeedsRules.required(2, 4, 5));
        assertEquals(6, ProgrammeNeedsRules.required(1, 4, 21));
    }

    @Test
    void matchesIgnoreCaseAndBlankMatchesAll() {
        assertTrue(ProgrammeNeedsRules.matches("Arc Welding Machine", "welding"));
        assertTrue(ProgrammeNeedsRules.matches("Anything", null));
        assertTrue(ProgrammeNeedsRules.matches("Anything", " "));
        assertFalse(ProgrammeNeedsRules.matches("Forklift", "welding"));
    }

    @Test
    void readinessIsAPromptNotAFinding() {
        assertEquals("NONE_HELD", ProgrammeNeedsRules.readiness(2, 0));
        assertEquals("SHORT", ProgrammeNeedsRules.readiness(2, 1));
        assertEquals("ENOUGH", ProgrammeNeedsRules.readiness(2, 2));
        assertTrue(ProgrammeNeedsRules.note("SHORT", 2, 1).contains("Check before the cohort starts"));
        assertFalse(ProgrammeNeedsRules.note("ENOUGH", 2, 2).toLowerCase().contains("complian"));
    }

    @Test
    void withdrawNeedsAReason() {
        assertNotNull(ProgrammeNeedsRules.withdrawProblem("short"));
        assertNull(ProgrammeNeedsRules.withdrawProblem("Replaced by a new line"));
    }

    @Test
    void sessionNotesAreAPromptToCheckNeverAFinding() {
        assertEquals("Booked for this session: 2; the programme lists 2", ProgrammeNeedsRules.slotNote(ProgrammeNeedsRules.ENOUGH, 2, 2));
        assertTrue(ProgrammeNeedsRules.slotNote(ProgrammeNeedsRules.SHORT, 3, 1).endsWith("Check the bookings before the session"));
        assertTrue(ProgrammeNeedsRules.slotNote(ProgrammeNeedsRules.NONE_HELD, 3, 0).startsWith("Nothing is booked for this session"));
        assertFalse(ProgrammeNeedsRules.slotNote(ProgrammeNeedsRules.SHORT, 3, 1).toLowerCase().contains("complian"));
    }
}
