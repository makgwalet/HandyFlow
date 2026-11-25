package za.co.handyflow.platform.trainingprovider.domain.rules;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class PractitionerRulesTest {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 25);

    @Test
    void anUncheckedAppointmentIsNeverInForce() {
        assertEquals(PractitionerRules.Standing.NOT_VERIFIED, PractitionerRules.standing("ACTIVE", null, null, null, DAY));
        assertEquals(PractitionerRules.Standing.VERIFIED_IN_FORCE, PractitionerRules.standing("ACTIVE", DAY.minusDays(3), null, null, DAY));
    }

    @Test
    void datesAndWithdrawalComeBeforeVerification() {
        LocalDate checked = DAY.minusDays(30);
        assertEquals(PractitionerRules.Standing.EXPIRED, PractitionerRules.standing("ACTIVE", checked, null, DAY.minusDays(1), DAY));
        assertEquals(PractitionerRules.Standing.VERIFIED_IN_FORCE, PractitionerRules.standing("ACTIVE", checked, null, DAY, DAY));
        assertEquals(PractitionerRules.Standing.NOT_YET_VALID, PractitionerRules.standing("ACTIVE", checked, DAY.plusDays(1), null, DAY));
        assertEquals(PractitionerRules.Standing.VERIFIED_IN_FORCE, PractitionerRules.standing("ACTIVE", checked, DAY, null, DAY));
        assertEquals(PractitionerRules.Standing.WITHDRAWN, PractitionerRules.standing("WITHDRAWN", checked, null, null, DAY));
    }

    @Test
    void everyStandingHasAPlainNote() {
        for (PractitionerRules.Standing s : PractitionerRules.Standing.values()) {
            assertNotNull(s.note());
            assertFalse(s.note().isBlank());
        }
        assertTrue(PractitionerRules.Standing.NOT_VERIFIED.note().contains("no record"));
    }

    @Test
    void appointmentChecks() {
        assertNull(PractitionerRules.appointmentProblem("ASSESSOR", null, null));
        assertNotNull(PractitionerRules.appointmentProblem("TEACHER", null, null));
        assertNotNull(PractitionerRules.appointmentProblem(null, null, null));
        assertTrue(PractitionerRules.appointmentProblem("ASSESSOR", DAY, DAY.minusDays(1)).contains("cannot end"));
    }

    @Test
    void verifyNeedsASourceADateAndForAssessorsACredential() {
        assertNotNull(PractitionerRules.verifyProblem("FACILITATOR", null, "abc", DAY, DAY));
        assertNotNull(PractitionerRules.verifyProblem("FACILITATOR", null, "Authority register", null, DAY));
        assertTrue(PractitionerRules.verifyProblem("FACILITATOR", null, "Authority register", DAY.plusDays(1), DAY).contains("future"));
        assertNull(PractitionerRules.verifyProblem("FACILITATOR", null, "Authority register", DAY, DAY));
        assertTrue(PractitionerRules.verifyProblem("ASSESSOR", null, "Authority register", DAY, DAY).contains("credential reference"));
        assertTrue(PractitionerRules.verifyProblem("MODERATOR", " ", "Authority register", DAY, DAY).contains("credential reference"));
        assertNull(PractitionerRules.verifyProblem("ASSESSOR", "REF-123", "Authority register", DAY, DAY));
    }

    @Test
    void namesContactAndReasonChecks() {
        assertNotNull(PractitionerRules.namesProblem("A", "Smith"));
        assertNull(PractitionerRules.namesProblem("Test", "Person"));
        assertNotNull(PractitionerRules.contactProblem("PARTNER", null));
        assertNotNull(PractitionerRules.contactProblem("EMPLOYEE", "nope"));
        assertNull(PractitionerRules.contactProblem("CONTRACTOR", "a@b.co"));
        assertNotNull(PractitionerRules.reasonProblem("short"));
        assertNull(PractitionerRules.reasonProblem("Left the company"));
    }

    @Test
    void onlyAssessorsAndModeratorsNeedACredential() {
        assertTrue(PractitionerRules.needsCredential("ASSESSOR"));
        assertTrue(PractitionerRules.needsCredential("MODERATOR"));
        assertFalse(PractitionerRules.needsCredential("FACILITATOR"));
    }
}
