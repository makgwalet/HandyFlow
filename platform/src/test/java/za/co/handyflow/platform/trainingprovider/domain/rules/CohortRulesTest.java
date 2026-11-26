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

    private static final LocalDate D1 = LocalDate.of(2027, 3, 1);
    private static final LocalDate D2 = LocalDate.of(2027, 6, 30);
    private static final CohortRules.Cover COVERED = new CohortRules.Cover(true, "COVERED", "Covered by a current approval");
    private static final CohortRules.Cover PENDING = new CohortRules.Cover(false, "NOT_APPROVED", "The accreditation is submitted. It is not an approval");

    private static CohortRules.DeliveryFacts facts(boolean offered, LocalDate offeredFrom, boolean accredited, CohortRules.Cover a, CohortRules.Cover b) {
        return new CohortRules.DeliveryFacts(true, true, offered, offeredFrom, accredited, a, b);
    }

    @Test
    void aCohortNeedsALiveOfferingAtTheSite() {
        assertNotNull(CohortRules.deliveryProblem(facts(false, null, false, null, null), D1, D2));
        assertNull(CohortRules.deliveryProblem(facts(true, D1.minusDays(10), false, null, null), D1, D2));
    }

    @Test
    void theOfferingMustHaveStartedBeforeTheCohort() {
        assertTrue(CohortRules.deliveryProblem(facts(true, D1.plusDays(1), false, null, null), D1, D2).contains("from " + D1.plusDays(1)));
        assertNull(CohortRules.deliveryProblem(facts(true, D1, false, null, null), D1, null));
    }

    @Test
    void anInactiveProgrammeOrSiteIsRefused() {
        assertNotNull(CohortRules.deliveryProblem(new CohortRules.DeliveryFacts(false, true, true, null, false, null, null), D1, D2));
        assertNotNull(CohortRules.deliveryProblem(new CohortRules.DeliveryFacts(true, false, true, null, false, null, null), D1, D2));
    }

    @Test
    void anAccreditedProgrammeNeedsCoverOnTheFirstAndLastDay() {
        assertNull(CohortRules.deliveryProblem(facts(true, null, true, COVERED, COVERED), D1, D2));
        assertTrue(CohortRules.deliveryProblem(facts(true, null, true, PENDING, COVERED), D1, D2).contains("start"));
        assertTrue(CohortRules.deliveryProblem(facts(true, null, true, COVERED, PENDING), D1, D2).contains("run to " + D2));
        assertNull(CohortRules.deliveryProblem(facts(true, null, true, COVERED, null), D1, null));
    }

    @Test
    void pendingEvidenceIsNotCoverAndSaysSo() {
        assertTrue(CohortRules.deliveryProblem(facts(true, null, true, PENDING, null), D1, null).contains("not an approval"));
    }

    @Test
    void aNonAccreditedProgrammeIgnoresAccreditation() {
        assertNull(CohortRules.deliveryProblem(facts(true, null, false, PENDING, PENDING), D1, D2));
    }

    @Test
    void conditionalOrRenewalPendingCoverIsAllowedWithACaveat() {
        CohortRules.Cover cond = new CohortRules.Cover(true, "COVERED_WITH_CONDITIONS", "Covered, but the approval has conditions that must be met");
        CohortRules.Cover renew = new CohortRules.Cover(true, "COVERED_RENEWAL_PENDING", "Covered until the current expiry date. A renewal is pending, not yet approved");
        assertNull(CohortRules.deliveryProblem(facts(true, null, true, cond, renew), D1, D2));
        assertEquals(cond.message(), CohortRules.deliveryCaveat(facts(true, null, true, cond, COVERED)));
        assertEquals(renew.message(), CohortRules.deliveryCaveat(facts(true, null, true, COVERED, renew)));
        assertNull(CohortRules.deliveryCaveat(facts(true, null, true, COVERED, COVERED)));
        assertNull(CohortRules.deliveryCaveat(facts(true, null, false, null, null)));
    }
}
