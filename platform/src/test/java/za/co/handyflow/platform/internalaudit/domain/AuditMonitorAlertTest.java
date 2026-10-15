package za.co.handyflow.platform.internalaudit.domain;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.internalaudit.domain.model.AuditMonitorAlert;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AuditMonitorAlertTest {
    UUID tenant = UUID.randomUUID(), ann = UUID.randomUUID(), bob = UUID.randomUUID(), finding = UUID.randomUUID();

    AuditMonitorAlert alert() {
        return AuditMonitorAlert.open(tenant, "DUPLICATE_JOURNAL", "MEDIUM", "JOURNAL", UUID.randomUUID().toString(), ann + "," + UUID.randomUUID(), "fp", "Two journals look the same", "d", new BigDecimal("10"), null);
    }

    @Test
    void startsNewAndOpen() {
        var a = alert();
        assertEquals("NEW", a.getStatus());
        assertFalse(a.isClosed());
    }

    @Test
    void takingItUpNeedsNoNoteButClosingDoes() {
        var a = alert();
        a.review("in_review", null, null, bob, "Bob");
        assertEquals("IN_REVIEW", a.getStatus());
        assertFalse(a.isClosed());
        assertThrows(IllegalArgumentException.class, () -> a.review("DISMISSED", "ok", null, bob, "Bob"));
        assertThrows(IllegalArgumentException.class, () -> a.review("ESCALATED", null, null, bob, "Bob"));
        a.review("DISMISSED", "Rent paid in two parts on purpose.", null, bob, "Bob");
        assertTrue(a.isClosed());
        assertEquals("Bob", a.getReviewedByName());
        assertEquals("Rent paid in two parts on purpose.", a.getDisposition());
    }

    @Test
    void aClosedAlertStaysClosed() {
        var a = alert();
        a.review("DISMISSED", "Checked with the supplier.", null, bob, "Bob");
        assertThrows(IllegalStateException.class, () -> a.review("IN_REVIEW", null, null, bob, "Bob"));
        assertThrows(IllegalStateException.class, () -> a.review("ESCALATED", "Changed my mind about it.", null, bob, "Bob"));
    }

    @Test
    void aPersonTheAlertIsAboutCannotReviewIt() {
        var a = alert();
        assertTrue(a.involves(ann));
        assertFalse(a.involves(bob));
        assertFalse(a.involves(null));
        assertThrows(IllegalStateException.class, () -> a.review("DISMISSED", "It was fine, trust me.", null, ann, "Ann"));
        assertThrows(IllegalStateException.class, () -> a.review("IN_REVIEW", null, null, ann, "Ann"));
        assertEquals("NEW", a.getStatus());
    }

    @Test
    void onlyEscalationCanLinkAFinding() {
        var a = alert();
        assertThrows(IllegalArgumentException.class, () -> a.review("DISMISSED", "Checked and fine here.", finding, bob, "Bob"));
        a.review("ESCALATED", "Needs a proper look by the team.", finding, bob, "Bob");
        assertEquals(finding, a.getFindingId());
    }

    @Test
    void badStatusRefused() {
        assertThrows(IllegalArgumentException.class, () -> alert().review("DONE", "Long enough note.", null, bob, "Bob"));
        assertThrows(IllegalArgumentException.class, () -> alert().review(null, "Long enough note.", null, bob, "Bob"));
    }

    @Test
    void longSummariesAreCut() {
        var a = AuditMonitorAlert.open(tenant, "R", "LOW", "USER", "x", null, "fp", "x".repeat(900), null, null, null);
        assertEquals(500, a.getSummary().length());
        assertFalse(a.involves(ann));
    }
}
