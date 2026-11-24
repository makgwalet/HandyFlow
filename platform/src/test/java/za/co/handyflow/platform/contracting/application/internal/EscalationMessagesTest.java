package za.co.handyflow.platform.contracting.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.contracting.domain.model.ApprovalAction;
import za.co.handyflow.platform.contracting.domain.model.EscalationRules.Stage;

import static org.junit.jupiter.api.Assertions.*;

class EscalationMessagesTest {

    static final String EVIL = "<script>alert('x')</script> & \"quoted\"";

    @Test
    void everyActionHasAReadableLabel() {
        for (ApprovalAction a : ApprovalAction.values()) {
            String label = EscalationMessages.actionLabel(a);
            assertNotNull(label);
            assertFalse(label.contains("_"), a.name());
        }
    }

    @Test
    void durationsAreReadable() {
        assertEquals("0 min", EscalationMessages.duration(0));
        assertEquals("0 min", EscalationMessages.duration(-5));
        assertEquals("45 min", EscalationMessages.duration(45));
        assertEquals("2 h", EscalationMessages.duration(120));
        assertEquals("25 h 30 min", EscalationMessages.duration(25 * 60 + 30));
    }

    @Test
    void escapingStopsMarkupGettingThrough() {
        assertEquals("&lt;b&gt; &amp; &quot;x&quot; &#39;y&#39;", EscalationMessages.escape("<b> & \"x\" 'y'"));
        assertEquals("", EscalationMessages.escape(null));
    }

    @Test
    void aReminderNamesTheActionTheSubjectAndTheWait() {
        var m = EscalationMessages.reminder(Stage.REMINDER_1, ApprovalAction.TERMINATE, "C-0042 Supply agreement", "Ann Smith", "23 Nov 2026 09:10 SAST",
                24 * 60, true, "https://app.example/contracts/approvals", false);
        assertTrue(m.subject().startsWith("Reminder:"));
        assertTrue(m.subject().contains("Terminate contract"));
        assertTrue(m.subject().contains("C-0042 Supply agreement"));
        assertTrue(m.html().contains("Ann Smith"));
        assertTrue(m.html().contains("24 h of working time"));
        assertTrue(m.html().contains("href=\"https://app.example/contracts/approvals\""));
    }

    @Test
    void theSecondReminderSaysSecondAndACalendarWaitDoesNotSayWorkingTime() {
        var m = EscalationMessages.reminder(Stage.REMINDER_2, ApprovalAction.PUBLISH_CLAUSE, "Indemnity", "Ann", "when", 48 * 60, false, "l", false);
        assertTrue(m.subject().startsWith("Second reminder:"));
        assertTrue(m.html().contains("48 h."));
        assertFalse(m.html().contains("working time"));
    }

    @Test
    void aRedirectedReminderSaysWhy() {
        var normal = EscalationMessages.reminder(Stage.REMINDER_1, ApprovalAction.TERMINATE, "s", "r", "w", 1, true, "l", false);
        var redirected = EscalationMessages.reminder(Stage.REMINDER_1, ApprovalAction.TERMINATE, "s", "r", "w", 1, true, "l", true);
        assertFalse(normal.html().contains("are away"));
        assertTrue(redirected.html().contains("are away"));
    }

    @Test
    void aReminderCannotBeBuiltForTheEscalationStep() {
        assertThrows(IllegalArgumentException.class, () -> EscalationMessages.reminder(Stage.ESCALATED, ApprovalAction.TERMINATE, "s", "r", "w", 1, true, "l", false));
        assertThrows(IllegalArgumentException.class, () -> EscalationMessages.reminder(Stage.NONE, ApprovalAction.TERMINATE, "s", "r", "w", 1, true, "l", false));
    }

    @Test
    void anEscalationSaysNothingWasDecidedOnAnyonesBehalf() {
        var m = EscalationMessages.escalation(ApprovalAction.SEND_FOR_SIGNING, "C-1 Lease", "Ann", "when", 72 * 60, true, "l", "Ben Jones, Cara Lee", false);
        assertTrue(m.subject().startsWith("Escalated:"));
        assertTrue(m.html().contains("Nothing has been approved or rejected"));
        assertTrue(m.html().contains("Already reminded: Ben Jones, Cara Lee."));
        assertFalse(m.html().contains("No earlier reminder"));
    }

    @Test
    void anEscalationThatSkippedRemindersSaysSo() {
        var m = EscalationMessages.escalation(ApprovalAction.SEND_FOR_SIGNING, "s", "r", "w", 1, true, "l", "", true);
        assertTrue(m.html().contains("No earlier reminder was sent"));
        assertFalse(m.html().contains("Already reminded"));
    }

    @Test
    void theRequesterIsToldWhetherAnyoneCouldBeReached() {
        assertTrue(EscalationMessages.toRequester(ApprovalAction.TERMINATE, "s", 4320, true, "l", true).html().contains("gets looked at"));
        assertTrue(EscalationMessages.toRequester(ApprovalAction.TERMINATE, "s", 4320, true, "l", false).html().contains("no one available"));
    }

    @Test
    void anAssignmentNamesWhoAssignedItAndCarriesTheNote() {
        var m = EscalationMessages.assigned(ApprovalAction.TERMINATE, "C-9", "Dee Admin", "Ann", "when", 90, true, "l", "You know this client");
        assertTrue(m.subject().startsWith("Approval assigned to you:"));
        assertTrue(m.html().contains("Dee Admin has asked you"));
        assertTrue(m.html().contains("Note: You know this client"));
        assertFalse(EscalationMessages.assigned(ApprovalAction.TERMINATE, "C-9", "Dee", "Ann", "when", 90, true, "l", null).html().contains("Note:"));
    }

    @Test
    void nothingTypedByAUserReachesTheMessageAsMarkup() {
        var mails = new EscalationMessages.Mail[]{
                EscalationMessages.reminder(Stage.REMINDER_1, ApprovalAction.TERMINATE, EVIL, EVIL, EVIL, 60, true, "https://x/\"><script>", false),
                EscalationMessages.escalation(ApprovalAction.TERMINATE, EVIL, EVIL, EVIL, 60, true, "l", EVIL, false),
                EscalationMessages.assigned(ApprovalAction.TERMINATE, EVIL, EVIL, EVIL, EVIL, 60, true, "l", EVIL)};
        for (var m : mails) {
            assertFalse(m.html().contains("<script>"), m.subject());
            assertFalse(m.html().contains("alert('x')"), m.subject());
        }
    }
}
