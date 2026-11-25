package za.co.handyflow.platform.trainingprovider.domain.rules;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class DocumentRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2027, 6, 10);

    @Test
    void letterNeedsStandingCompletedOutcome() {
        assertNull(DocumentRules.issueProblem("COMPLETION_LETTER", "ENROLLED", false, "COMPLETED", "RECORDED"));
        assertNotNull(DocumentRules.issueProblem("COMPLETION_LETTER", "ENROLLED", false, null, null));
        assertNotNull(DocumentRules.issueProblem("COMPLETION_LETTER", "ENROLLED", false, "DID_NOT_COMPLETE", "RECORDED"));
        assertNotNull(DocumentRules.issueProblem("COMPLETION_LETTER", "ENROLLED", false, "COMPLETED", "WITHDRAWN"));
    }

    @Test
    void statementNeedsNoOutcomeButNotCancelledOrDuplicate() {
        assertNull(DocumentRules.issueProblem("PARTICIPATION_STATEMENT", "IN_PROGRESS", false, null, null));
        assertNotNull(DocumentRules.issueProblem("PARTICIPATION_STATEMENT", "CANCELLED", false, null, null));
        assertNotNull(DocumentRules.issueProblem("PARTICIPATION_STATEMENT", "IN_PROGRESS", true, null, null));
        assertNotNull(DocumentRules.issueProblem("OTHER", "IN_PROGRESS", false, null, null));
    }

    @Test
    void wordingNeverClaimsAnAuthoritysCertificate() {
        String body = DocumentRules.body("COMPLETION_LETTER", "A B", "Safety", 2, TODAY).toLowerCase();
        String statement = DocumentRules.body("PARTICIPATION_STATEMENT", "A B", "Safety", null, null).toLowerCase();
        for (String s : new String[]{body, statement, DocumentRules.DISCLAIMER.toLowerCase().replace("not a certificate", "")}) {
            assertFalse(s.contains("competent"));
            assertFalse(s.contains("accredited"));
            assertFalse(s.contains("registered with"));
        }
        assertTrue(DocumentRules.DISCLAIMER.contains("QCTO"));
        assertTrue(DocumentRules.DISCLAIMER.contains("SAQA"));
        assertTrue(statement.contains("participation only"));
    }

    @Test
    void handOverChecks() {
        LocalDate issued = LocalDate.of(2027, 6, 1);
        assertNull(DocumentRules.handOverProblem("ISSUED", false, TODAY, issued, TODAY, "The learner", "IN_PERSON"));
        assertNotNull(DocumentRules.handOverProblem("VOID", false, TODAY, issued, TODAY, "The learner", "IN_PERSON"));
        assertNotNull(DocumentRules.handOverProblem("ISSUED", true, TODAY, issued, TODAY, "The learner", "IN_PERSON"));
        assertNotNull(DocumentRules.handOverProblem("ISSUED", false, TODAY.plusDays(1), issued, TODAY, "The learner", "IN_PERSON"));
        assertNotNull(DocumentRules.handOverProblem("ISSUED", false, issued.minusDays(1), issued, TODAY, "The learner", "IN_PERSON"));
        assertNotNull(DocumentRules.handOverProblem("ISSUED", false, TODAY, issued, TODAY, " ", "IN_PERSON"));
        assertNotNull(DocumentRules.handOverProblem("ISSUED", false, TODAY, issued, TODAY, "The learner", "FAX"));
    }

    @Test
    void voidNeedsReasonAndLiveDocument() {
        assertNull(DocumentRules.voidProblem("ISSUED", "Issued to the wrong enrolment"));
        assertNotNull(DocumentRules.voidProblem("ISSUED", "short"));
        assertNotNull(DocumentRules.voidProblem("VOID", "Issued to the wrong enrolment"));
    }
}
