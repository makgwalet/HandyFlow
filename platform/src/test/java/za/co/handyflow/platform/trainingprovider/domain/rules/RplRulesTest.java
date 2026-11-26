package za.co.handyflow.platform.trainingprovider.domain.rules;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RplRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2027, 6, 10);
    private static final LocalDate START = LocalDate.of(2027, 6, 1);
    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-0000000000b1");

    @Test
    void applicationNeedsOpenEnrolmentClaimAndNoLiveDuplicate() {
        assertNull(RplRules.applicationProblem("IN_PROGRESS", START, "Ten years running stores", START, TODAY, true, false));
        assertNotNull(RplRules.applicationProblem("CANCELLED", START, "Ten years running stores", START, TODAY, true, false));
        assertNotNull(RplRules.applicationProblem("IN_PROGRESS", START, "short", START, TODAY, true, false));
        assertNotNull(RplRules.applicationProblem("IN_PROGRESS", START, "Ten years running stores", TODAY.plusDays(1), TODAY, true, false));
        assertNotNull(RplRules.applicationProblem("IN_PROGRESS", START, "Ten years running stores", START.minusDays(1), TODAY, true, false));
        assertNotNull(RplRules.applicationProblem("IN_PROGRESS", START, "Ten years running stores", START, TODAY, false, false));
        assertNotNull(RplRules.applicationProblem("IN_PROGRESS", START, "Ten years running stores", START, TODAY, true, true));
    }

    @Test
    void evidenceAndItsReviewAreChecked() {
        assertNull(RplRules.evidenceProblem("OPEN", "DOCUMENT", "Reference letter from employer", START, START, TODAY));
        assertNotNull(RplRules.evidenceProblem("DECIDED", "DOCUMENT", "Reference letter from employer", START, START, TODAY));
        assertNotNull(RplRules.evidenceProblem("OPEN", "PHOTO", "Reference letter from employer", START, START, TODAY));
        assertNotNull(RplRules.evidenceProblem("OPEN", "DOCUMENT", "short", START, START, TODAY));
        assertNotNull(RplRules.evidenceProblem("OPEN", "DOCUMENT", "Reference letter from employer", START.minusDays(1), START, TODAY));
        assertNull(RplRules.reviewProblem("OPEN", "RECEIVED", false, "ACCEPTED", "Letter checked against the employer", TODAY, START, TODAY));
        assertNotNull(RplRules.reviewProblem("OPEN", "RECEIVED", true, "ACCEPTED", "Letter checked against the employer", TODAY, START, TODAY));
        assertNotNull(RplRules.reviewProblem("OPEN", "WITHDRAWN", false, "ACCEPTED", "Letter checked against the employer", TODAY, START, TODAY));
        assertNotNull(RplRules.reviewProblem("OPEN", "RECEIVED", false, "MAYBE", "Letter checked against the employer", TODAY, START, TODAY));
        assertNotNull(RplRules.reviewProblem("OPEN", "RECEIVED", false, "QUERIED", "short", TODAY, START, TODAY));
        assertNotNull(RplRules.evidenceWithdrawProblem("OPEN", "RECEIVED", "short"));
        assertNull(RplRules.evidenceWithdrawProblem("OPEN", "RECEIVED", "Uploaded against the wrong learner"));
    }

    @Test
    void decisionNeedsReviewedEvidenceAndAWrittenBasis() {
        assertNull(RplRules.decisionProblem("OPEN", 2, 0, 1, "RECOGNISED", "Evidence matches the outcomes", "Ms Assessor", TODAY, START, TODAY, null, false));
        assertNotNull(RplRules.decisionProblem("OPEN", 0, 0, 0, "NOT_RECOGNISED", "No evidence was offered", "Ms Assessor", TODAY, START, TODAY, null, false));
        assertNotNull(RplRules.decisionProblem("OPEN", 2, 1, 1, "RECOGNISED", "Evidence matches the outcomes", "Ms Assessor", TODAY, START, TODAY, null, false));
        assertNotNull(RplRules.decisionProblem("OPEN", 2, 0, 0, "RECOGNISED", "Evidence matches the outcomes", "Ms Assessor", TODAY, START, TODAY, null, false));
        assertNotNull(RplRules.decisionProblem("OPEN", 2, 0, 0, "PARTLY_RECOGNISED", "Evidence matches the outcomes", "Ms Assessor", TODAY, START, TODAY, null, false));
        assertNull(RplRules.decisionProblem("OPEN", 2, 0, 0, "NOT_RECOGNISED", "Evidence did not match the outcomes", "Ms Assessor", TODAY, START, TODAY, null, false));
        assertNotNull(RplRules.decisionProblem("OPEN", 2, 0, 1, "RECOGNISED", "short", "Ms Assessor", TODAY, START, TODAY, null, false));
        assertNotNull(RplRules.decisionProblem("OPEN", 2, 0, 1, "RECOGNISED", "Evidence matches the outcomes", " ", TODAY, START, TODAY, null, false));
        assertNotNull(RplRules.decisionProblem("OPEN", 2, 0, 1, "RECOGNISED", "Evidence matches the outcomes", "Ms Assessor", TODAY.plusDays(1), START, TODAY, null, false));
        assertNotNull(RplRules.decisionProblem("DECIDED", 2, 0, 1, "RECOGNISED", "Evidence matches the outcomes", "Ms Assessor", TODAY, START, TODAY, null, false));
    }

    @Test
    void confirmingAloneNeedsAReasonAndASecondPersonMustDiffer() {
        assertNotNull(RplRules.decisionProblem("OPEN", 1, 0, 1, "RECOGNISED", "Evidence matches the outcomes", "Ms Assessor", TODAY, START, TODAY, "short", true));
        assertNull(RplRules.decisionProblem("OPEN", 1, 0, 1, "RECOGNISED", "Evidence matches the outcomes", "Ms Assessor", TODAY, START, TODAY, "Only one qualified assessor on site", true));
        assertNull(RplRules.confirmProblem("DECIDED", A, B, TODAY, START, TODAY));
        assertNotNull(RplRules.confirmProblem("DECIDED", A, A, TODAY, START, TODAY));
        assertNotNull(RplRules.confirmProblem("OPEN", A, B, TODAY, START, TODAY));
        assertNotNull(RplRules.confirmProblem("CONFIRMED", A, B, TODAY, START, TODAY));
        assertNotNull(RplRules.confirmProblem("DECIDED", A, B, START.minusDays(1), START, TODAY));
        assertNotNull(RplRules.confirmProblem("DECIDED", A, B, TODAY.plusDays(1), START, TODAY));
    }

    @Test
    void withdrawNeedsAReasonAndWordingNeverClaimsMore() {
        assertNotNull(RplRules.withdrawProblem("OPEN", "short"));
        assertNull(RplRules.withdrawProblem("CONFIRMED", "Entered against the wrong learner"));
        assertNotNull(RplRules.withdrawProblem("WITHDRAWN", "Entered against the wrong learner"));
        String label = RplRules.stateLabel("CONFIRMED", "RECOGNISED", false);
        assertTrue(label.contains("not a completion or a certificate"));
        assertFalse(label.toLowerCase().contains("competent"));
        assertTrue(RplRules.stateLabel("CONFIRMED", "RECOGNISED", true).contains("written reason"));
        assertTrue(RplRules.stateLabel("DECIDED", "PARTLY_RECOGNISED", false).contains("partly recognised"));
    }

    @Test
    void anRplAssessorNeedsAnRplAppointmentCheckedAndInForce() {
        PractitionerRules.Standing ok = PractitionerRules.Standing.VERIFIED_IN_FORCE;
        assertNull(RplRules.assessorProblem(true, true, ok, true, false, null));
        assertTrue(RplRules.assessorProblem(true, false, ok, true, false, null).contains("ordinary assessor appointment"));
        assertNotNull(RplRules.assessorProblem(false, true, ok, true, false, null));
        for (PractitionerRules.Standing st : PractitionerRules.Standing.values()) {
            if (st != ok) {
                assertNotNull(RplRules.assessorProblem(true, true, st, true, false, null));
            }
        }
    }

    @Test
    void anAssessorLinkedToAnotherUserCannotBeDecidedFor() {
        assertTrue(RplRules.assessorProblem(true, true, PractitionerRules.Standing.VERIFIED_IN_FORCE, true, true, null).contains("Only the assessor"));
    }

    @Test
    void anAssessorWithNoLoginNeedsAWrittenNote() {
        PractitionerRules.Standing ok = PractitionerRules.Standing.VERIFIED_IN_FORCE;
        assertNotNull(RplRules.assessorProblem(true, true, ok, false, false, null));
        assertNotNull(RplRules.assessorProblem(true, true, ok, false, false, "short"));
        assertNull(RplRules.assessorProblem(true, true, ok, false, false, "Signed decision form handed over"));
    }

    @Test
    void rplAssessorIsItsOwnRoleThatNeedsACredential() {
        assertTrue(PractitionerRules.ROLES.contains("RPL_ASSESSOR"));
        assertTrue(PractitionerRules.needsCredential("RPL_ASSESSOR"));
        assertFalse(PractitionerRules.ASSESSOR.equals(PractitionerRules.RPL_ASSESSOR));
    }
}
