package za.co.handyflow.platform.clinic.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.clinic.application.internal.IssueOnceRules.Action;
import za.co.handyflow.platform.clinic.application.internal.IssueOnceRules.WhenChanged;

import static org.junit.jupiter.api.Assertions.*;

class IssueOnceRulesTest {

    @Test void nothingIssuedYetIsIssued() {
        assertEquals(Action.ISSUE, IssueOnceRules.decide(false, false, false, WhenChanged.ASK_FOR_REASON, false));
    }

    @Test void anUnchangedDocumentIsHandedBackUnderTheSameNumber() {
        assertEquals(Action.REUSE, IssueOnceRules.decide(true, true, true, WhenChanged.ASK_FOR_REASON, false));
    }

    @Test void anUnchangedDocumentWhosePdfWasNotKeptIsReplaced() {
        assertEquals(Action.REPLACE, IssueOnceRules.decide(true, true, false, WhenChanged.ASK_FOR_REASON, false));
    }

    @Test void aChangedVisitSummaryReplacesTheOldOne() {
        assertEquals(Action.REPLACE, IssueOnceRules.decide(true, false, true, WhenChanged.SUPERSEDE, false));
    }

    @Test void aChangedPrescriptionNeedsAReasonBeforeItIsReissued() {
        assertEquals(Action.REFUSE, IssueOnceRules.decide(true, false, true, WhenChanged.ASK_FOR_REASON, false));
        assertEquals(Action.REPLACE, IssueOnceRules.decide(true, false, true, WhenChanged.ASK_FOR_REASON, true));
    }

    @Test void aDeliberateReissueReplacesEvenWhenNothingChanged() {
        assertEquals(Action.REPLACE, IssueOnceRules.decide(true, true, true, WhenChanged.ASK_FOR_REASON, true));
    }

    @Test void theReissueReasonMustSaySomething() {
        assertNull(IssueOnceRules.reason("  "));
        assertThrows(IllegalArgumentException.class, () -> IssueOnceRules.reason("lost"));
        assertEquals("Original was lost", IssueOnceRules.reason("  Original was lost "));
        assertThrows(IllegalArgumentException.class, () -> IssueOnceRules.reason("x".repeat(501)));
        assertEquals("Reissued: Original was lost", IssueOnceRules.replacedBecause("Prescription", "Original was lost"));
        assertEquals("Visit summary replaced by an updated copy", IssueOnceRules.replacedBecause("Visit summary", null));
    }
}
