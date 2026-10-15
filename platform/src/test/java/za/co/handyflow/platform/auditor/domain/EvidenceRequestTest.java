package za.co.handyflow.platform.auditor.domain;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.auditor.domain.model.EvidenceRequest;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class EvidenceRequestTest {
    UUID tenant = UUID.randomUUID(), staff = UUID.randomUUID(), auditor = UUID.randomUUID();

    private EvidenceRequest open() {
        return EvidenceRequest.create(tenant, null, "ER-0001", "Bank statements March", null, "HIGH", LocalDate.of(2026, 11, 1), null, "AUDITOR", auditor, "a@firm.test");
    }

    @Test
    void needsTitleAndKnownPriorityAndRequester() {
        assertThrows(IllegalArgumentException.class, () -> EvidenceRequest.create(tenant, null, "ER-1", " ", null, "LOW", null, null, "STAFF", staff, "x"));
        assertThrows(IllegalArgumentException.class, () -> EvidenceRequest.create(tenant, null, "ER-1", "T", null, "URGENT", null, null, "STAFF", staff, "x"));
        assertThrows(IllegalArgumentException.class, () -> EvidenceRequest.create(tenant, null, "ER-1", "T", null, "LOW", null, null, "OTHER", staff, "x"));
        assertEquals("MEDIUM", EvidenceRequest.create(tenant, null, "ER-1", "T", null, null, null, null, "STAFF", staff, "x").getPriority());
    }

    @Test
    void sendingNeedsAtLeastOneFile() {
        var r = open();
        assertThrows(IllegalStateException.class, () -> r.submit(staff, "here", Set.of()));
        r.submit(staff, "here", Set.of(UUID.randomUUID()));
        assertEquals("SUBMITTED", r.getStatus());
        assertEquals(1, r.getRounds().size());
    }

    @Test
    void cannotSendTwiceWhileWaitingForADecision() {
        var r = open();
        r.submit(staff, "here", Set.of(UUID.randomUUID()));
        assertThrows(IllegalStateException.class, () -> r.submit(staff, "again", Set.of(UUID.randomUUID())));
        assertThrows(IllegalStateException.class, () -> r.markNotAvailable(staff, "we do not have it at all"));
    }

    @Test
    void sentBackReopensKeepsHistoryAndNeedsAReason() {
        var r = open();
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        r.submit(staff, "here", Set.of(first));
        assertThrows(IllegalArgumentException.class, () -> r.reject(auditor, "bad"));
        r.reject(auditor, "Statement is missing page 2");
        assertEquals("OPEN", r.getStatus());
        assertEquals(1, r.getTimesRejected());
        r.submit(staff, "replacement", Set.of(second));
        assertEquals(2, r.getRounds().size());
        assertEquals("REJECTED", r.getRounds().get(0).getDecision());
        assertEquals(Set.of(first, second), r.allSentEvidence());
        r.approve(auditor, null);
        assertEquals("APPROVED", r.getStatus());
        assertTrue(r.isFinished());
    }

    @Test
    void notAvailableNeedsAReasonAndAuditorCanAcceptOrRefuseIt() {
        var r = open();
        assertThrows(IllegalArgumentException.class, () -> r.markNotAvailable(staff, "no"));
        r.markNotAvailable(staff, "The branch lost the paper copy in a flood");
        assertEquals("NOT_AVAILABLE", r.getStatus());
        r.reject(auditor, "Get a copy from the bank instead");
        assertEquals("OPEN", r.getStatus());
        r.markNotAvailable(staff, "The bank only keeps seven years of statements");
        r.approve(auditor, "Accepted, noted in the file");
        assertEquals("CLOSED_NOT_AVAILABLE", r.getStatus());
    }

    @Test
    void nothingToDecideWhenOpen() {
        var r = open();
        assertThrows(IllegalStateException.class, () -> r.approve(auditor, null));
        assertThrows(IllegalStateException.class, () -> r.reject(auditor, "Some good reason here"));
    }

    @Test
    void finishedRequestsCannotBeChanged() {
        var r = open();
        r.cancel("Asked for in error, not needed");
        assertEquals("CANCELLED", r.getStatus());
        assertThrows(IllegalStateException.class, () -> r.assign(staff));
        assertThrows(IllegalStateException.class, () -> r.cancel("Trying to cancel again"));
        assertThrows(IllegalStateException.class, () -> r.submit(staff, "x", Set.of(UUID.randomUUID())));
    }

    @Test
    void overdueAndDueSoonOnlyWhileOpen() {
        var r = open(); // due 2026-11-01
        assertTrue(r.overdueOn(LocalDate.of(2026, 11, 2)));
        assertFalse(r.overdueOn(LocalDate.of(2026, 11, 1)));
        assertTrue(r.dueSoonOn(LocalDate.of(2026, 10, 30)));
        assertFalse(r.dueSoonOn(LocalDate.of(2026, 10, 20)));
        r.submit(staff, "here", Set.of(UUID.randomUUID()));
        assertFalse(r.overdueOn(LocalDate.of(2026, 11, 2)));
    }
}
