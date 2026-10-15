package za.co.handyflow.platform.auditor.domain;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.auditor.domain.model.AuditQuery;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AuditQueryTest {
    UUID tenant = UUID.randomUUID(), auditor = UUID.randomUUID(), staff = UUID.randomUUID();

    private AuditQuery raised() {
        return AuditQuery.raise(tenant, null, "QUERY", "Q-0001", "Round-sum journal", "Why was journal 1042 posted for exactly R500,000?",
                "JOURNAL_ENTRY", UUID.randomUUID(), "JE-1042", LocalDate.of(2026, 11, 1), auditor, "a@firm.test");
    }

    @Test
    void theQuestionIsTheFirstMessageAndItNeedsSubstance() {
        var q = raised();
        assertEquals(1, q.getMessages().size());
        assertEquals("QUESTION", q.getMessages().get(0).getMessageKind());
        assertThrows(IllegalArgumentException.class, () -> AuditQuery.raise(tenant, null, "QUERY", "Q-2", "T", "why?", null, null, null, null, auditor, "a"));
        assertThrows(IllegalArgumentException.class, () -> AuditQuery.raise(tenant, null, "QUERY", "Q-2", " ", "A proper question here", null, null, null, null, auditor, "a"));
        assertThrows(IllegalArgumentException.class, () -> AuditQuery.raise(tenant, null, "OTHER", "Q-2", "T", "A proper question here", null, null, null, null, auditor, "a"));
        assertThrows(IllegalArgumentException.class, () -> AuditQuery.raise(tenant, null, "QUERY", "Q-2", "T", "A proper question here", "SECRET", null, null, null, auditor, "a"));
    }

    @Test
    void answerThenAcceptResolves() {
        var q = raised();
        q.answer(staff, "Thandi", "It was the agreed monthly retainer accrual");
        assertEquals("ANSWERED", q.getStatus());
        q.resolve(auditor, "a@firm.test", null);
        assertEquals("RESOLVED", q.getStatus());
        assertTrue(q.isFinished());
        assertNotNull(q.getClosedAt());
    }

    @Test
    void followUpReopensAndCounts() {
        var q = raised();
        q.answer(staff, "Thandi", "It was the agreed monthly retainer accrual");
        assertThrows(IllegalArgumentException.class, () -> q.followUp(auditor, "a", "show me"));
        q.followUp(auditor, "a", "Please show the retainer agreement");
        assertEquals("OPEN", q.getStatus());
        assertEquals(1, q.getTimesReopened());
        assertEquals(3, q.getMessages().size());
    }

    @Test
    void cannotAnswerTwiceOrResolveWithoutAnAnswer() {
        var q = raised();
        assertThrows(IllegalStateException.class, () -> q.resolve(auditor, "a", null));
        assertThrows(IllegalStateException.class, () -> q.followUp(auditor, "a", "A follow-up question here"));
        q.answer(staff, "T", "An answer with enough detail");
        assertThrows(IllegalStateException.class, () -> q.answer(staff, "T", "Another answer with detail"));
    }

    @Test
    void withdrawnNeedsAReasonAndEndsIt() {
        var q = raised();
        assertThrows(IllegalArgumentException.class, () -> q.withdraw(auditor, "a", "no"));
        q.withdraw(auditor, "a", "Answered by the schedule we already hold");
        assertEquals("WITHDRAWN", q.getStatus());
        assertThrows(IllegalStateException.class, () -> q.answer(staff, "T", "Too late but with detail"));
        assertThrows(IllegalStateException.class, () -> q.withdraw(auditor, "a", "Withdrawing again for reasons"));
    }

    @Test
    void linkingARecordsTheConnectionOnly() {
        var q = raised();
        UUID finding = UUID.randomUUID();
        q.linkToFinding(finding, staff);
        assertEquals(finding, q.getLinkedFindingId());
        assertEquals("OPEN", q.getStatus());
        q.unlinkFinding();
        assertNull(q.getLinkedFindingId());
        assertThrows(IllegalArgumentException.class, () -> q.linkToFinding(null, staff));
    }

    @Test
    void overdueOnlyWhileWaitingForTheBusiness() {
        var q = raised(); // due 2026-11-01
        assertTrue(q.overdueOn(LocalDate.of(2026, 11, 2)));
        q.answer(staff, "T", "An answer with enough detail");
        assertFalse(q.overdueOn(LocalDate.of(2026, 11, 2)));
    }
}
