package za.co.handyflow.platform.trainingprovider.domain.rules;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AssessmentRulesTest {

    @Test
    void instrumentShapeIsChecked() {
        assertNull(AssessmentRules.instrumentProblem("Safety theory test", "KNOWLEDGE", new BigDecimal("50")));
        assertNull(AssessmentRules.instrumentProblem("Portfolio of evidence", "PORTFOLIO", null));
        assertNotNull(AssessmentRules.instrumentProblem(" ", "KNOWLEDGE", null));
        assertNotNull(AssessmentRules.instrumentProblem("Test", "ORAL", null));
        assertNotNull(AssessmentRules.instrumentProblem("Test", null, null));
        assertNotNull(AssessmentRules.instrumentProblem("Test", "PRACTICAL", BigDecimal.ZERO));
        assertNotNull(AssessmentRules.instrumentProblem("Test", "PRACTICAL", new BigDecimal("-5")));
    }

    @Test
    void moduleMustBelongToTheSameVersion() {
        UUID v = UUID.randomUUID();
        assertNull(AssessmentRules.moduleProblem(v, v));
        assertNotNull(AssessmentRules.moduleProblem(UUID.randomUUID(), v));
    }

    @Test
    void onlyDraftsAreEdited() {
        assertNull(AssessmentRules.editProblem("DRAFT"));
        assertNotNull(AssessmentRules.editProblem("APPROVED"));
        assertNotNull(AssessmentRules.editProblem("RETIRED"));
    }

    @Test
    void approvalNeedsASecondPersonOrAWrittenReason() {
        assertNull(AssessmentRules.approvalProblem("DRAFT", false, null));
        assertNotNull(AssessmentRules.approvalProblem("DRAFT", true, null));
        assertNotNull(AssessmentRules.approvalProblem("DRAFT", true, "too short"));
        assertNull(AssessmentRules.approvalProblem("DRAFT", true, "Only one qualified person on site this week"));
        assertNotNull(AssessmentRules.approvalProblem("APPROVED", false, null));
    }

    @Test
    void retirementNeedsAReasonAndHappensOnce() {
        assertNull(AssessmentRules.retireProblem("DRAFT", "Replaced by the new version"));
        assertNull(AssessmentRules.retireProblem("APPROVED", "Replaced by the new version"));
        assertNotNull(AssessmentRules.retireProblem("APPROVED", "short"));
        assertNotNull(AssessmentRules.retireProblem("RETIRED", "Replaced by the new version"));
    }

    @Test
    void statusMovesForwardOnly() {
        assertTrue(AssessmentRules.nextStatuses("DRAFT").contains("APPROVED"));
        assertTrue(AssessmentRules.nextStatuses("APPROVED").contains("RETIRED"));
        assertFalse(AssessmentRules.nextStatuses("APPROVED").contains("DRAFT"));
        assertTrue(AssessmentRules.nextStatuses("RETIRED").isEmpty());
    }
}
