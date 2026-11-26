package za.co.handyflow.platform.trainingprovider.domain.rules;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class LearnershipRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2027, 6, 10);
    private static final LocalDate START = LocalDate.of(2027, 6, 1);

    @Test
    void agreementNeedsOpenEnrolmentEmployerDaysAndNoLiveDuplicate() {
        assertNull(LearnershipRules.agreementProblem("IN_PROGRESS", "Acme Stores", START, START.plusMonths(12), START, false));
        assertNotNull(LearnershipRules.agreementProblem("CANCELLED", "Acme Stores", START, null, START, false));
        assertNotNull(LearnershipRules.agreementProblem("IN_PROGRESS", "A", START, null, START, false));
        assertNotNull(LearnershipRules.agreementProblem("IN_PROGRESS", "Acme Stores", null, null, START, false));
        assertNotNull(LearnershipRules.agreementProblem("IN_PROGRESS", "Acme Stores", START, START.minusDays(1), START, false));
        assertNotNull(LearnershipRules.agreementProblem("IN_PROGRESS", "Acme Stores", START.minusYears(2), null, START, false));
        assertNotNull(LearnershipRules.agreementProblem("IN_PROGRESS", "Acme Stores", START, null, START, true));
    }

    @Test
    void onlyADraftCanBeChangedOrSigned() {
        assertNull(LearnershipRules.editProblem("DRAFT"));
        assertNotNull(LearnershipRules.editProblem("SIGNED"));
        assertNull(LearnershipRules.signProblem("DRAFT", START, START, START, TODAY));
        assertNotNull(LearnershipRules.signProblem("SIGNED", START, START, START, TODAY));
        assertNotNull(LearnershipRules.signProblem("DRAFT", null, START, START, TODAY));
        assertNotNull(LearnershipRules.signProblem("DRAFT", START, START, TODAY.plusDays(1), TODAY));
    }

    @Test
    void aRegistrationReferenceIsRecordedOnceAndOnlyOnASignedAgreement() {
        assertNull(LearnershipRules.registrationProblem("SIGNED", false, "REG/123", TODAY, TODAY));
        assertNotNull(LearnershipRules.registrationProblem("DRAFT", false, "REG/123", TODAY, TODAY));
        assertNotNull(LearnershipRules.registrationProblem("SIGNED", true, "REG/123", TODAY, TODAY));
        assertNotNull(LearnershipRules.registrationProblem("SIGNED", false, "R", TODAY, TODAY));
        assertNotNull(LearnershipRules.registrationProblem("SIGNED", false, "REG/123", TODAY.plusDays(1), TODAY));
        assertNotNull(LearnershipRules.registrationProblem("SIGNED", false, "REG/123", null, TODAY));
    }

    @Test
    void endingAndCancellingNeedReasonsAndRightStates() {
        assertNull(LearnershipRules.endProblem("SIGNED", TODAY, START, TODAY, "Learner completed the term"));
        assertNotNull(LearnershipRules.endProblem("DRAFT", TODAY, START, TODAY, "Learner completed the term"));
        assertNotNull(LearnershipRules.endProblem("SIGNED", START.minusDays(1), START, TODAY, "Learner completed the term"));
        assertNotNull(LearnershipRules.endProblem("SIGNED", TODAY, START, TODAY, "short"));
        assertNull(LearnershipRules.cancelProblem("DRAFT", false, "Employer pulled out"));
        assertNull(LearnershipRules.cancelProblem("SIGNED", false, "Signed against the wrong learner"));
        assertNotNull(LearnershipRules.cancelProblem("SIGNED", true, "Signed against the wrong learner"));
        assertNotNull(LearnershipRules.cancelProblem("ENDED", false, "Signed against the wrong learner"));
        assertNotNull(LearnershipRules.cancelProblem("DRAFT", false, "short"));
        assertEquals(START.plusDays(2), LearnershipRules.latest(START, START.plusDays(2), START.plusDays(1)));
    }

    @Test
    void summaryNeverSaysRegisteredOrApproved() {
        String s = LearnershipRules.summary("SIGNED", START, START, START, "REG/123");
        assertTrue(s.contains("record of the paper agreement"));
        assertTrue(s.contains("does not confirm that the learnership is registered or approved"));
        assertTrue(s.contains("REG/123"));
        assertTrue(s.contains("has not been checked against any register"));
        assertFalse(LearnershipRules.summary("DRAFT", null, null, null, null).contains("signed by"));
    }
}
