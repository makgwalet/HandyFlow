package za.co.handyflow.platform.trainingprovider.domain.rules;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PortalRulesTest {

    @Test
    void inviteNeedsAValidEmailAReasonAndAnActiveLearner() {
        assertNull(PortalRules.inviteProblem("thandi@example.co.za", "Learner asked to see her timetable", "ACTIVE"));
        assertNotNull(PortalRules.inviteProblem("not-an-email", "Learner asked to see her timetable", "ACTIVE"));
        assertNotNull(PortalRules.inviteProblem(null, "Learner asked to see her timetable", "ACTIVE"));
        assertTrue(PortalRules.inviteProblem("a@b.co", "short", "ACTIVE").contains("at least 10"));
        assertEquals("Only an active learner can be given portal access", PortalRules.inviteProblem("a@b.co", "Learner asked to see her timetable", "ARCHIVED"));
    }

    @Test
    void endingNeedsAReasonAndALiveGrant() {
        assertNull(PortalRules.endProblem("Learner left the programme", "ACCEPTED"));
        assertNull(PortalRules.endProblem("Wrong email address used", "PENDING"));
        assertEquals("This access has already ended", PortalRules.endProblem("Learner left the programme", "ENDED"));
        assertTrue(PortalRules.endProblem("  short ", "ACCEPTED").contains("at least 10"));
    }

    @Test
    void anEmployerSeesOnlyConsentedLearnersThatWereNotMerged() {
        assertTrue(PortalRules.employerMayView(true, false));
        assertFalse(PortalRules.employerMayView(false, false));
        assertFalse(PortalRules.employerMayView(true, true));
    }

    @Test
    void attendanceIsCountsOnly() {
        PortalRules.Counts c = PortalRules.count(List.of("PRESENT", "PRESENT", "LATE", "ABSENT", "EXCUSED", "OTHER"));
        assertEquals(5, c.recorded());
        assertEquals(2, c.present());
        assertEquals(1, c.late());
        assertEquals(1, c.absent());
        assertEquals(1, c.excused());
        assertEquals(0, PortalRules.count(List.of()).recorded());
    }

    @Test
    void outcomeWordsNeverSayCompetent() {
        assertEquals("Recorded as completed", PortalRules.outcomeText("COMPLETED"));
        assertEquals("Recorded as not completed", PortalRules.outcomeText("DID_NOT_COMPLETE"));
        assertEquals("No outcome recorded yet", PortalRules.outcomeText(null));
        for (String o : new String[]{"COMPLETED", "DID_NOT_COMPLETE", null}) assertFalse(PortalRules.outcomeText(o).toLowerCase().contains("competent"));
    }
}
