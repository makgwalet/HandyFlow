package za.co.handyflow.platform.trainingprovider.domain.rules;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class ResourceRulesTest {
    private static final LocalDate TODAY = LocalDate.of(2027, 3, 10);

    @Test
    void basicsAreChecked() {
        assertNull(ResourceRules.problem("Welding rig", "EQUIPMENT", 2));
        assertNotNull(ResourceRules.problem("W", "EQUIPMENT", 2));
        assertNotNull(ResourceRules.problem("Welding rig", "MAGIC", 2));
        assertNotNull(ResourceRules.problem("Welding rig", "EQUIPMENT", 0));
        assertNotNull(ResourceRules.problem("Welding rig", "EQUIPMENT", null));
        assertNotNull(ResourceRules.problem("Welding rig", "EQUIPMENT", 100001));
    }

    @Test
    void stateMovesFollowTheRegister() {
        assertTrue(ResourceRules.nextStatuses("IN_SERVICE").contains("UNDER_REPAIR"));
        assertTrue(ResourceRules.nextStatuses("UNDER_REPAIR").contains("IN_SERVICE"));
        assertTrue(ResourceRules.nextStatuses("RETIRED").isEmpty());
        assertEquals("A retired resource stays retired", ResourceRules.statusProblem("RETIRED", "IN_SERVICE", "Found it again today", TODAY, TODAY));
        assertEquals("That change of state is not allowed", ResourceRules.statusProblem("IN_SERVICE", "IN_SERVICE", "Nothing changed here", TODAY, TODAY));
    }

    @Test
    void aStateChangeNeedsAReasonAndASaneDay() {
        assertNull(ResourceRules.statusProblem("IN_SERVICE", "UNDER_REPAIR", "Hydraulic leak found on the arm", TODAY, TODAY));
        assertNotNull(ResourceRules.statusProblem("IN_SERVICE", "UNDER_REPAIR", "short", TODAY, TODAY));
        assertNotNull(ResourceRules.statusProblem("IN_SERVICE", "UNDER_REPAIR", "Hydraulic leak found on the arm", null, TODAY));
        assertEquals("The change cannot be dated in the future", ResourceRules.statusProblem("IN_SERVICE", "UNDER_REPAIR", "Hydraulic leak found on the arm", TODAY.plusDays(1), TODAY));
        assertEquals("Choose a state", ResourceRules.statusProblem("IN_SERVICE", "BROKEN", "Hydraulic leak found on the arm", TODAY, TODAY));
    }

    @Test
    void movesNeedAnActiveOtherSiteAndAReason() {
        assertNull(ResourceRules.moveProblem("IN_SERVICE", false, true, true, "Needed for the new cohort"));
        assertEquals("A retired resource cannot be moved", ResourceRules.moveProblem("RETIRED", false, true, true, "Needed for the new cohort"));
        assertEquals("It is already at that site", ResourceRules.moveProblem("IN_SERVICE", true, true, true, "Needed for the new cohort"));
        assertEquals("That site is not active", ResourceRules.moveProblem("IN_SERVICE", false, false, true, "Needed for the new cohort"));
        assertEquals("That venue is not at the site", ResourceRules.moveProblem("IN_SERVICE", false, true, false, "Needed for the new cohort"));
        assertNotNull(ResourceRules.moveProblem("IN_SERVICE", false, true, true, "short"));
    }
}
