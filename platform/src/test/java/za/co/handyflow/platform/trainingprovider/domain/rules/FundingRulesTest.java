package za.co.handyflow.platform.trainingprovider.domain.rules;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FundingRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2027, 6, 10);
    private static final LocalDate START = LocalDate.of(2027, 6, 1);

    @Test
    void funderNeedsKindNameAndASaneEmail() {
        assertNull(FundingRules.funderProblem("EMPLOYER", "Acme Stores", "hr@acme.co.za", false));
        assertNull(FundingRules.funderProblem("SELF", "Self-funded", null, false));
        assertNotNull(FundingRules.funderProblem("BURSARY", "Acme", null, false));
        assertNotNull(FundingRules.funderProblem(null, "Acme", null, false));
        assertNotNull(FundingRules.funderProblem("SETA", "A", null, false));
        assertNotNull(FundingRules.funderProblem("SETA", "Some SETA", "not-an-email", false));
        assertNotNull(FundingRules.funderProblem("SETA", "Some SETA", null, true));
    }

    @Test
    void retiringNeedsAReasonAndNoActiveLinks() {
        assertNull(FundingRules.retireProblem("ACTIVE", "Stopped sponsoring learners", 0));
        assertNotNull(FundingRules.retireProblem("ACTIVE", "short", 0));
        assertNotNull(FundingRules.retireProblem("ACTIVE", "Stopped sponsoring learners", 2));
        assertNotNull(FundingRules.retireProblem("RETIRED", "Stopped sponsoring learners", 0));
        assertNull(FundingRules.editProblem("ACTIVE"));
        assertNotNull(FundingRules.editProblem("RETIRED"));
    }

    @Test
    void linkingChecksEnrolmentFunderDayAndDuplicates() {
        assertNull(FundingRules.linkProblem("IN_PROGRESS", "ACTIVE", START, START, TODAY, false));
        assertNotNull(FundingRules.linkProblem("CANCELLED", "ACTIVE", START, START, TODAY, false));
        assertNotNull(FundingRules.linkProblem("IN_PROGRESS", "RETIRED", START, START, TODAY, false));
        assertNotNull(FundingRules.linkProblem("IN_PROGRESS", "ACTIVE", null, START, TODAY, false));
        assertNotNull(FundingRules.linkProblem("IN_PROGRESS", "ACTIVE", TODAY.plusYears(2), START, TODAY, false));
        assertNotNull(FundingRules.linkProblem("IN_PROGRESS", "ACTIVE", START.minusYears(2), START, TODAY, false));
        assertNotNull(FundingRules.linkProblem("IN_PROGRESS", "ACTIVE", START, START, TODAY, true));
    }

    @Test
    void endingNeedsADayAndAReason() {
        assertNull(FundingRules.endProblem("ACTIVE", TODAY, START, TODAY, "Employer withdrew sponsorship"));
        assertNotNull(FundingRules.endProblem("ENDED", TODAY, START, TODAY, "Employer withdrew sponsorship"));
        assertNotNull(FundingRules.endProblem("ACTIVE", TODAY.plusDays(1), START, TODAY, "Employer withdrew sponsorship"));
        assertNotNull(FundingRules.endProblem("ACTIVE", START.minusDays(1), START, TODAY, "Employer withdrew sponsorship"));
        assertNotNull(FundingRules.endProblem("ACTIVE", TODAY, START, TODAY, "short"));
    }

    @Test
    void summaryNeverClaimsApprovalOrPayment() {
        assertEquals("No funder is recorded for this enrolment.", FundingRules.summary(List.of()));
        String s = FundingRules.summary(List.of("Acme Stores", "Some SETA"));
        assertTrue(s.contains("Acme Stores, Some SETA"));
        assertTrue(s.contains("holds no amounts"));
        assertTrue(s.contains("does not mean any funding was approved or paid"));
    }
}
