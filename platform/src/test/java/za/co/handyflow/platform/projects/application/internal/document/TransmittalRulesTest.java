package za.co.handyflow.platform.projects.application.internal.document;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.projects.domain.enums.DocumentStatus;

import static org.junit.jupiter.api.Assertions.*;

class TransmittalRulesTest {

    @Test
    void constructionIssues_needAnApprovedDocument() {
        assertTrue(TransmittalRules.requiresApproved("FOR_CONSTRUCTION"));
        assertTrue(TransmittalRules.requiresApproved("AS_BUILT"));
        assertFalse(TransmittalRules.requiresApproved("FOR_INFORMATION"));
        assertFalse(TransmittalRules.requiresApproved(null));
    }

    @Test
    void problem_isNullWhenTheDocumentIsFitForThePurpose() {
        assertNull(TransmittalRules.problem("FOR_CONSTRUCTION", DocumentStatus.APPROVED));
        assertNull(TransmittalRules.problem("FOR_INFORMATION", DocumentStatus.CURRENT));
        assertNull(TransmittalRules.problem("FOR_INFORMATION", DocumentStatus.FOR_REVIEW));
    }

    @Test
    void problem_explainsWhyAnUnfitDocumentIsRefused() {
        assertNotNull(TransmittalRules.problem("FOR_CONSTRUCTION", DocumentStatus.CURRENT));
        assertNotNull(TransmittalRules.problem("AS_BUILT", DocumentStatus.FOR_REVIEW));
        assertNotNull(TransmittalRules.problem("FOR_INFORMATION", DocumentStatus.VOIDED));
        assertNotNull(TransmittalRules.problem("FOR_INFORMATION", DocumentStatus.SUPERSEDED));
        assertNotNull(TransmittalRules.problem("FOR_INFORMATION", DocumentStatus.REJECTED));
    }
}
