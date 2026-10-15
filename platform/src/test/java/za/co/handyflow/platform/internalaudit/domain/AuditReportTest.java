package za.co.handyflow.platform.internalaudit.domain;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.internalaudit.domain.model.*;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AuditReportTest {
    UUID tenant = UUID.randomUUID(), eng = UUID.randomUUID(), me = UUID.randomUUID();

    AuditReport draft() { return AuditReport.draft(tenant, eng, 1, "STANDARD", "Payroll audit report", me); }

    @Test
    void reviewNeedsAtLeastOneReviewer() {
        assertThrows(IllegalStateException.class, () -> draft().startFactualReview(0));
        var r = draft();
        r.startFactualReview(1);
        assertEquals("FACTUAL_REVIEW", r.getStatus());
        assertNotNull(r.getFactReviewStartedAt());
    }

    @Test
    void reviewCannotCloseWhileCommentsAreOpen() {
        var r = draft();
        r.startFactualReview(2);
        assertThrows(IllegalStateException.class, () -> r.closeFactualReview(1));
        r.closeFactualReview(0);
        assertEquals("READY", r.getStatus());
    }

    @Test
    void issueNeedsClosedReviewAndSignOff() {
        var r = draft();
        assertThrows(IllegalStateException.class, () -> r.issue(me, "h", true));          // not through review
        r.startFactualReview(1);
        assertThrows(IllegalStateException.class, () -> r.issue(me, "h", true));          // review still open
        r.closeFactualReview(0);
        assertThrows(IllegalStateException.class, () -> r.issue(me, "h", false));         // not signed off
        r.issue(me, "abc", true);
        assertEquals("ISSUED", r.getStatus());
        assertEquals("abc", r.getContentHash());
    }

    @Test
    void issuedReportIsFrozen() {
        var r = draft();
        r.startFactualReview(1); r.closeFactualReview(0); r.issue(me, "abc", true);
        assertThrows(IllegalStateException.class, r::requireEditable);
        assertThrows(IllegalStateException.class, () -> r.rename("Other"));
        assertThrows(IllegalStateException.class, r::reopen);
        r.supersede();
        assertEquals("SUPERSEDED", r.getStatus());
        assertTrue(r.isFinal());
    }

    @Test
    void reopeningSendsItBackThroughReview() {
        var r = draft();
        r.startFactualReview(1); r.closeFactualReview(0);
        r.reopen();
        assertEquals("DRAFT", r.getStatus());
        assertNull(r.getFactReviewClosedAt());
        assertThrows(IllegalStateException.class, () -> r.issue(me, "h", true));
    }

    @Test
    void factCommentsNeedRealTextAndAnswersAreOnce() {
        UUID report = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> AuditReportFactItem.raise(tenant, report, 1, "wrong", me, "Bob"));
        var i = AuditReportFactItem.raise(tenant, report, 1, "The headcount is 41, not 14", me, "Bob");
        assertThrows(IllegalArgumentException.class, () -> i.respond("MAYBE", "Will look at it later", me, "Thandi"));
        assertThrows(IllegalArgumentException.class, () -> i.respond("ACCEPTED", "ok", me, "Thandi"));
        i.respond("accepted", "Corrected in section 2", me, "Thandi");
        assertEquals("ACCEPTED", i.getStatus());
        assertThrows(IllegalStateException.class, () -> i.respond("DECLINED", "Changed my mind about it", me, "Thandi"));
    }

    @Test
    void recipientViewsAreCounted() {
        var p = AuditReportPerson.of(tenant, UUID.randomUUID(), "RECIPIENT", UUID.randomUUID(), "Committee chair", me);
        assertEquals(0, p.getViewCount());
        p.recordView(); p.recordView();
        assertEquals(2, p.getViewCount());
        assertNotNull(p.getFirstViewedAt());
        assertThrows(IllegalArgumentException.class, () -> AuditReportPerson.of(tenant, UUID.randomUUID(), "OBSERVER", UUID.randomUUID(), "x", me));
    }
}
