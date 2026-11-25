package za.co.handyflow.platform.trainingprovider.domain.rules;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CohortRulesTest {

    private static final UUID V1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID V2 = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID S1 = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID S2 = UUID.fromString("00000000-0000-0000-0000-0000000000a2");

    @Test
    void cohortLifecycle() {
        assertNull(CohortRules.transitionProblem("PLANNED", "RUNNING", null));
        assertNull(CohortRules.transitionProblem("RUNNING", "COMPLETED", null));
        assertNotNull(CohortRules.transitionProblem("PLANNED", "COMPLETED", null));
        assertNotNull(CohortRules.transitionProblem("COMPLETED", "RUNNING", null));
        assertNotNull(CohortRules.transitionProblem("CANCELLED", "PLANNED", null));
    }

    @Test
    void cancellingNeedsAWrittenReason() {
        assertNotNull(CohortRules.transitionProblem("PLANNED", "CANCELLED", "short"));
        assertNotNull(CohortRules.transitionProblem("RUNNING", "CANCELLED", null));
        assertNull(CohortRules.transitionProblem("RUNNING", "CANCELLED", "Too few learners enrolled"));
    }

    @Test
    void cohortAndVenueChecks() {
        LocalDate d = LocalDate.of(2027, 2, 1);
        assertNotNull(CohortRules.cohortProblem("A", d, null, null));
        assertNotNull(CohortRules.cohortProblem("Feb intake", null, null, null));
        assertNotNull(CohortRules.cohortProblem("Feb intake", d, d.minusDays(1), null));
        assertNotNull(CohortRules.cohortProblem("Feb intake", d, null, 0));
        assertNull(CohortRules.cohortProblem("Feb intake", d, d, 20));
        assertNotNull(CohortRules.venueProblem("R", null));
        assertNotNull(CohortRules.venueProblem("Room 1", 0));
        assertNull(CohortRules.venueProblem("Room 1", 25));
    }

    @Test
    void aLearnerMustMatchTheCohortVersionAndSite() {
        assertNull(CohortRules.memberProblem("PLANNED", V1, S1, "ENROLLED", V1, S1, false, 3, 10));
        assertNotNull(CohortRules.memberProblem("COMPLETED", V1, S1, "ENROLLED", V1, S1, false, 3, 10));
        assertNotNull(CohortRules.memberProblem("PLANNED", V1, S1, "WITHDRAWN", V1, S1, false, 3, 10));
        assertNotNull(CohortRules.memberProblem("PLANNED", V1, S1, "ON_HOLD", V1, S1, false, 3, 10));
        assertTrue(CohortRules.memberProblem("PLANNED", V1, S1, "ENROLLED", V2, S1, false, 3, 10).contains("version"));
        assertTrue(CohortRules.memberProblem("PLANNED", V1, S1, "ENROLLED", V1, S2, false, 3, 10).contains("site"));
    }

    @Test
    void aLearnerIsInOneLiveCohortAndCapacityIsRespected() {
        assertTrue(CohortRules.memberProblem("RUNNING", V1, S1, "IN_PROGRESS", V1, S1, true, 3, 10).contains("already in a cohort"));
        assertTrue(CohortRules.memberProblem("RUNNING", V1, S1, "IN_PROGRESS", V1, S1, false, 10, 10).contains("full"));
        assertNull(CohortRules.memberProblem("RUNNING", V1, S1, "IN_PROGRESS", V1, S1, false, 9, 10));
        assertNull(CohortRules.memberProblem("RUNNING", V1, S1, "IN_PROGRESS", V1, S1, false, 500, null));
    }

    @Test
    void onlyACheckedInForceFacilitatorOfAnActivePersonCanBeAssigned() {
        PractitionerRules.Standing ok = PractitionerRules.Standing.VERIFIED_IN_FORCE;
        assertNull(CohortRules.facilitatorProblem("PLANNED", true, ok, false));
        assertNotNull(CohortRules.facilitatorProblem("CANCELLED", true, ok, false));
        assertNotNull(CohortRules.facilitatorProblem("PLANNED", false, ok, false));
        assertNotNull(CohortRules.facilitatorProblem("PLANNED", true, ok, true));
        for (PractitionerRules.Standing s : PractitionerRules.Standing.values()) {
            if (s != ok) {
                assertNotNull(CohortRules.facilitatorProblem("RUNNING", true, s, false));
            }
        }
        assertTrue(CohortRules.facilitatorProblem("RUNNING", true, PractitionerRules.Standing.NOT_VERIFIED, false).contains("no record"));
    }

    @Test
    void reasons() {
        assertNotNull(CohortRules.reasonProblem(null));
        assertNotNull(CohortRules.reasonProblem("short"));
        assertNull(CohortRules.reasonProblem("Moved to the next intake"));
    }
}
