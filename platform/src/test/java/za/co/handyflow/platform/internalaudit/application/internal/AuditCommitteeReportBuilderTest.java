package za.co.handyflow.platform.internalaudit.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.internalaudit.application.internal.AuditCommitteeReportBuilder.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AuditCommitteeReportBuilderTest {
    LocalDate today = LocalDate.of(2026, 10, 15);
    UUID u1 = UUID.randomUUID(), u2 = UUID.randomUUID(), u3 = UUID.randomUUID();
    ControlFigures noControls = new ControlFigures(0, 0, 0, 0, 0, 0);

    Report build(List<PlanEntryIn> entries, List<UniverseIn> uni, List<FindingIn> fs, List<ActionIn> acts, List<ReportIn> reps) {
        return AuditCommitteeReportBuilder.build(today, 2026, "ACTIVE", entries, uni, fs, acts, reps, noControls);
    }

    @Test
    void planDeliveryByQuarterIgnoresDeferred() {
        var r = build(List.of(new PlanEntryIn(u1, 1, "COMPLETED"), new PlanEntryIn(u2, 1, "IN_PROGRESS"), new PlanEntryIn(u3, 2, "DEFERRED"),
                new PlanEntryIn(UUID.randomUUID(), 3, "PLANNED")), List.of(), List.of(), List.of(), List.of());
        assertEquals(3, r.plan().committed());
        assertEquals(1, r.plan().completed());
        assertEquals(33, r.plan().completionPct());
        assertEquals(1, r.plan().deferred());
        assertEquals(List.of(1, 3), r.plan().quarters().stream().map(QuarterRow::quarter).toList());
        assertEquals(2, r.plan().quarters().get(0).planned());
        assertEquals(1, r.plan().quarters().get(0).completed());
    }

    @Test
    void reportsApprovedAwaitingAndClosedWithoutApproval() {
        var r = build(List.of(), List.of(), List.of(), List.of(), List.of(
                new ReportIn("A", "CLOSED", "APPROVED", Instant.parse("2026-09-01T00:00:00Z")),
                new ReportIn("B", "CLOSED", "APPROVED", Instant.parse("2026-10-01T00:00:00Z")),
                new ReportIn("C", "REPORTING", "SUBMITTED", null),
                new ReportIn("D", "CLOSED", null, null),
                new ReportIn("E", "CLOSED", "REJECTED", null),
                new ReportIn("F", "FIELDWORK", null, null)));
        assertEquals(2, r.reports().approved());
        assertEquals(1, r.reports().awaitingSignOff());
        assertEquals(2, r.reports().closedWithoutApproval());
        assertEquals("B", r.reports().recentlyApproved().get(0).engagement());   // newest first
    }

    @Test
    void riskProfileCountsLevelsAndNamesHighRiskAreasNotYetAudited() {
        var r = build(List.of(new PlanEntryIn(u1, 1, "COMPLETED")), List.of(new UniverseIn(u1, "Payroll", "HIGH"), new UniverseIn(u2, "Treasury", "critical"),
                new UniverseIn(u3, "Stores", null)), List.of(), List.of(), List.of());
        assertEquals(List.of("CRITICAL", "HIGH", "UNASSESSED"), r.risk().byLevel().stream().map(RiskRow::level).toList());
        assertEquals(List.of("Treasury"), r.risk().highRiskNotYetAudited());
    }

    @Test
    void onlyHighRiskOverdueActionsAreReportedOldestFirst() {
        UUID hi = UUID.randomUUID(), crit = UUID.randomUUID(), low = UUID.randomUUID();
        var fs = List.of(new FindingIn(hi, "Weak approvals", "HIGH", "OPEN", today.minusDays(3)), new FindingIn(crit, "Open payment run", "CRITICAL", "IN_PROGRESS", today.plusDays(3)),
                new FindingIn(low, "Typos", "LOW", "OPEN", today.minusDays(30)), new FindingIn(UUID.randomUUID(), "Closed one", "HIGH", "CLOSED", today.minusDays(9)));
        var acts = List.of(new ActionIn(UUID.randomUUID(), hi, "Fix approvals", today.minusDays(5), "Thandi"), new ActionIn(UUID.randomUUID(), crit, "Lock payment run", today.minusDays(12), "Bob"),
                new ActionIn(UUID.randomUUID(), low, "Spell check", today.minusDays(40), "Sam"), new ActionIn(UUID.randomUUID(), hi, "Not late", today.plusDays(2), "Thandi"));
        var r = build(List.of(), List.of(), fs, acts, List.of());
        assertEquals(2, r.overdueHighRiskActionCount());
        assertEquals("Lock payment run", r.overdueHighRiskActions().get(0).description());
        assertEquals(12, r.overdueHighRiskActions().get(0).daysOverdue());
        assertEquals("CRITICAL", r.overdueHighRiskActions().get(0).severity());
        assertEquals(2, r.findings().openHighOrCritical());
        assertEquals(1, r.findings().pastDue());
    }

    @Test
    void emptyTenantGivesZerosAndKeepsControlFigures() {
        var c = new ControlFigures(5, 2, 1, 1, 0, 3);
        var r = AuditCommitteeReportBuilder.build(today, null, null, List.of(), List.of(), List.of(), List.of(), List.of(), c);
        assertEquals(0, r.plan().completionPct());
        assertEquals(0, r.overdueHighRiskActionCount());
        assertEquals(c, r.controls());
    }
}
