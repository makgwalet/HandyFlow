package za.co.handyflow.platform.auditor.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.auditor.application.internal.ExternalAuditDashboardBuilder.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ExternalAuditDashboardBuilderTest {
    LocalDate today = LocalDate.of(2026, 10, 15);
    UUID e1 = UUID.randomUUID(), e2 = UUID.randomUUID();
    List<EngagementIn> engs = List.of(new EngagementIn(e1, "FY2026 audit", "ACTIVE"), new EngagementIn(e2, "Interim", "ACTIVE"));

    RequestIn req(UUID e, String status, LocalDate due, int rejected) { return new RequestIn(e, "ER-1", "T", status, due, rejected); }
    QueryIn q(UUID e, String kind, String status, LocalDate due, int reopened) { return new QueryIn(e, kind, "Q-1", "T", status, due, reopened); }

    @Test
    void requestsSplitByWhoIsWaitingOnWhom() {
        var d = ExternalAuditDashboardBuilder.build(engs, List.of(
                req(e1, "OPEN", today.minusDays(3), 1), req(e1, "OPEN", today.plusDays(2), 0), req(e1, "OPEN", today.plusDays(20), 0),
                req(e1, "SUBMITTED", today, 0), req(e1, "APPROVED", null, 0), req(e1, "CLOSED_NOT_AVAILABLE", null, 0), req(e1, "CANCELLED", null, 0)),
                List.of(), null, today);
        var r = d.requests();
        assertEquals(3, r.awaitingBusiness());
        assertEquals(1, r.overdue());
        assertEquals(1, r.dueSoon());
        assertEquals(1, r.awaitingAuditor());
        assertEquals(1, r.rejectedBefore());
        assertEquals(1, r.approved());
        assertEquals(1, r.notAvailable());
    }

    @Test
    void queriesAndReviewNotesAreCountedSeparately() {
        var d = ExternalAuditDashboardBuilder.build(engs, List.of(), List.of(
                q(e1, "QUERY", "OPEN", today.minusDays(1), 2), q(e1, "QUERY", "ANSWERED", null, 0), q(e1, "QUERY", "RESOLVED", null, 0),
                q(e1, "REVIEW_NOTE", "OPEN", today.minusDays(5), 0), q(e1, "REVIEW_NOTE", "ANSWERED", null, 0), q(e1, "REVIEW_NOTE", "RESOLVED", null, 0)),
                null, today);
        assertEquals(1, d.queries().awaitingBusiness());
        assertEquals(1, d.queries().overdue());
        assertEquals(1, d.queries().awaitingAuditor());
        assertEquals(1, d.queries().reopenedBefore());
        assertEquals(1, d.queries().resolved());
        assertEquals(2, d.reviewNotes().unresolved());
        assertEquals(1, d.reviewNotes().overdue());
        assertEquals(2, d.overdueTotal());
    }

    @Test
    void filteringToOneEngagementLeavesOutTheOthers() {
        var d = ExternalAuditDashboardBuilder.build(engs, List.of(req(e1, "OPEN", today.minusDays(1), 0), req(e2, "OPEN", today.minusDays(1), 0)),
                List.of(q(e2, "QUERY", "OPEN", null, 0)), e1, today);
        assertEquals(1, d.requests().awaitingBusiness());
        assertEquals(0, d.queries().awaitingBusiness());
        assertEquals(1, d.engagements().size());
        assertEquals("FY2026 audit", d.engagements().get(0).name());
    }

    @Test
    void overdueItemsOldestFirstAndPerEngagementRows() {
        var d = ExternalAuditDashboardBuilder.build(engs, List.of(req(e1, "OPEN", today.minusDays(2), 0), req(e2, "OPEN", today.minusDays(9), 0)),
                List.of(q(e2, "QUERY", "OPEN", today.minusDays(4), 0)), null, today);
        assertEquals(List.of(9L, 4L, 2L), d.overdueItems().stream().map(OverdueItem::daysOverdue).toList());
        assertEquals("Interim", d.overdueItems().get(0).engagement());
        assertEquals("Interim", d.engagements().get(0).name());   // busiest first
        assertEquals(1, d.engagements().get(0).openQueries());
    }

    @Test
    void itemsWithNoEngagementAreShownAsSuch() {
        var d = ExternalAuditDashboardBuilder.build(engs, List.of(req(null, "OPEN", today.minusDays(1), 0)), List.of(), null, today);
        assertTrue(d.engagements().stream().anyMatch(r -> r.name().equals("No engagement") && r.openRequests() == 1));
    }

    @Test
    void emptyIsAllZeros() {
        var d = ExternalAuditDashboardBuilder.build(List.of(), List.of(), List.of(), null, today);
        assertEquals(0, d.overdueTotal());
        assertTrue(d.overdueItems().isEmpty());
    }
}
