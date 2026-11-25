package za.co.handyflow.platform.trainingprovider.domain.rules;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class EnrolmentRulesTest {

    private static final LocalDate START = LocalDate.of(2027, 2, 1);

    @Test
    void applicationsFollowTheirLifecycle() {
        assertNull(EnrolmentRules.applicationProblem("RECEIVED", "UNDER_REVIEW", null, null, null, null));
        assertTrue(EnrolmentRules.applicationProblem("RECEIVED", "ACCEPTED", null, true, "Checked the certificate", null).contains("cannot move"));
        assertTrue(EnrolmentRules.applicationProblem("DECLINED", "UNDER_REVIEW", null, null, null, null).contains("cannot move"));
        assertTrue(EnrolmentRules.applicationProblem("UNDER_REVIEW", "BOGUS", null, null, null, null).contains("Unknown"));
    }

    @Test
    void enrolledIsNeverSetByHand() {
        assertTrue(EnrolmentRules.applicationProblem("ACCEPTED", "ENROLLED", null, null, null, null).contains("when the learner is enrolled"));
    }

    @Test
    void declineWithdrawAndDocumentsNeedAReason() {
        for (String to : new String[]{"DECLINED", "WITHDRAWN"}) {
            assertNotNull(EnrolmentRules.applicationProblem("UNDER_REVIEW", to, "short", null, null, null));
            assertNull(EnrolmentRules.applicationProblem("UNDER_REVIEW", to, "A proper reason", null, null, null));
        }
        assertNotNull(EnrolmentRules.applicationProblem("UNDER_REVIEW", "DOCUMENTS_REQUESTED", null, null, null, null));
    }

    @Test
    void acceptingNeedsAPersonsRecordedEntryCheck() {
        assertTrue(EnrolmentRules.applicationProblem("UNDER_REVIEW", "ACCEPTED", null, null, null, null).contains("meets the entry"));
        assertTrue(EnrolmentRules.applicationProblem("UNDER_REVIEW", "ACCEPTED", null, true, "short", null).contains("what was checked"));
        assertNull(EnrolmentRules.applicationProblem("UNDER_REVIEW", "ACCEPTED", null, true, "Certificate sighted and copied", null));
    }

    @Test
    void acceptingSomeoneWhoDoesNotMeetEntryNeedsAWrittenReason() {
        assertTrue(EnrolmentRules.applicationProblem("UNDER_REVIEW", "ACCEPTED", null, false, "Certificate sighted and copied", null).contains("not met"));
        assertNull(EnrolmentRules.applicationProblem("UNDER_REVIEW", "ACCEPTED", null, false, "Certificate sighted and copied", "Recognised through prior experience"));
    }

    @Test
    void enrolmentsFollowTheirLifecycle() {
        assertNull(EnrolmentRules.enrolmentTransitionProblem("ENROLLED", "IN_PROGRESS", null));
        assertNotNull(EnrolmentRules.enrolmentTransitionProblem("IN_PROGRESS", "CANCELLED", "A reason long enough"));
        assertNotNull(EnrolmentRules.enrolmentTransitionProblem("WITHDRAWN", "IN_PROGRESS", null));
        assertNotNull(EnrolmentRules.enrolmentTransitionProblem("ENROLLED", "ON_HOLD", "short"));
        assertNull(EnrolmentRules.enrolmentTransitionProblem("ENROLLED", "ON_HOLD", "Waiting for the next intake"));
        assertNull(EnrolmentRules.enrolmentTransitionProblem("ON_HOLD", "IN_PROGRESS", null));
    }

    @Test
    void liveStatusesAreTheOnesThatBlockASecondEnrolment() {
        assertTrue(EnrolmentRules.isLive("ENROLLED"));
        assertTrue(EnrolmentRules.isLive("ON_HOLD"));
        assertFalse(EnrolmentRules.isLive("WITHDRAWN"));
        assertFalse(EnrolmentRules.isLive("CANCELLED"));
    }

    @Test
    void basicEnrolChecks() {
        assertTrue(EnrolmentRules.enrolProblem(false, "ACTIVE", true, true, "NON_ACCREDITED", null, false, null, START, null).contains("archived"));
        assertTrue(EnrolmentRules.enrolProblem(true, "DRAFT", true, true, "NON_ACCREDITED", null, false, null, START, null).contains("active programme"));
        assertTrue(EnrolmentRules.enrolProblem(true, "ACTIVE", false, true, "NON_ACCREDITED", null, false, null, START, null).contains("approved version"));
        assertTrue(EnrolmentRules.enrolProblem(true, "ACTIVE", true, false, "NON_ACCREDITED", null, false, null, START, null).contains("not offered"));
        assertTrue(EnrolmentRules.enrolProblem(true, "ACTIVE", true, true, "NON_ACCREDITED", null, false, null, null, null).contains("start date"));
        assertTrue(EnrolmentRules.enrolProblem(true, "ACTIVE", true, true, "NON_ACCREDITED", null, false, null, START, START.minusDays(1)).contains("end date"));
        assertNull(EnrolmentRules.enrolProblem(true, "ACTIVE", true, true, "NON_ACCREDITED", null, false, null, START, START.plusMonths(6)));
    }

    @Test
    void anAccreditedProgrammeNeedsCoverOrAnAdministratorsWrittenReason() {
        assertNull(EnrolmentRules.enrolProblem(true, "ACTIVE", true, true, "ACCREDITED", "COVERED", false, null, START, null));
        assertNull(EnrolmentRules.enrolProblem(true, "ACTIVE", true, true, "ACCREDITED", "COVERED_WITH_CONDITIONS", false, null, START, null));
        for (String outcome : new String[]{"NO_SCOPE", "SITE_NOT_COVERED", "EXPIRED", "SUSPENDED", "NOT_APPROVED", "SCOPE_WITHDRAWN", "SCOPE_NOT_IN_FORCE"}) {
            String p = EnrolmentRules.enrolProblem(true, "ACTIVE", true, true, "ACCREDITED", outcome, true, null, START, null);
            assertNotNull(p);
            assertTrue(p.contains("administrator"));
        }
        assertTrue(EnrolmentRules.enrolProblem(true, "ACTIVE", true, true, "ACCREDITED", "NO_SCOPE", false, "Approved by the board in writing", START, null).contains("Only an administrator"));
        assertNull(EnrolmentRules.enrolProblem(true, "ACTIVE", true, true, "ACCREDITED", "NO_SCOPE", true, "Approved by the board in writing", START, null));
    }

    @Test
    void coverageOnlyCountsTheThreeCoveredOutcomes() {
        assertTrue(EnrolmentRules.isCovered("COVERED_RENEWAL_PENDING"));
        assertFalse(EnrolmentRules.isCovered("EXPIRED"));
        assertFalse(EnrolmentRules.isCovered(null));
    }
}
