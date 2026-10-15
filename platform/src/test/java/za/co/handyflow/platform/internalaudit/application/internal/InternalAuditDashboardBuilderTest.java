package za.co.handyflow.platform.internalaudit.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.internalaudit.application.internal.InternalAuditDashboardBuilder.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class InternalAuditDashboardBuilderTest {
    LocalDate today = LocalDate.of(2026, 10, 15);
    UUID u1 = UUID.randomUUID(), u2 = UUID.randomUUID(), u3 = UUID.randomUUID(), u4 = UUID.randomUUID();
    List<UniverseIn> universe = List.of(new UniverseIn(u1, "Payroll"), new UniverseIn(u2, "Treasury"), new UniverseIn(u3, "Procurement"), new UniverseIn(u4, "Inventory"));

    @Test
    void planCompletionIgnoresDeferredEntries() {
        var d = InternalAuditDashboardBuilder.build(2026, "ACTIVE", List.of(
                new PlanEntryIn(u1, "COMPLETED"), new PlanEntryIn(u2, "IN_PROGRESS"), new PlanEntryIn(u3, "DEFERRED"), new PlanEntryIn(u4, "PLANNED")),
                universe, List.of(), List.of(), today);
        assertEquals(4, d.plan().total());
        assertEquals(1, d.plan().completed());
        assertEquals(33, d.plan().completionPct());   // 1 of 3 committed
        assertEquals(1, d.plan().deferred());
    }

    @Test
    void coverageCountsPlannedAndAuditedAndNamesWhatIsLeftOut() {
        var d = InternalAuditDashboardBuilder.build(2026, "ACTIVE", List.of(
                new PlanEntryIn(u1, "COMPLETED"), new PlanEntryIn(u2, "PLANNED"), new PlanEntryIn(u3, "DEFERRED")),
                universe, List.of(), List.of(), today);
        assertEquals(4, d.coverage().universeSize());
        assertEquals(2, d.coverage().planned());
        assertEquals(1, d.coverage().audited());
        assertEquals(50, d.coverage().plannedPct());
        assertEquals(25, d.coverage().auditedPct());
        assertEquals(List.of("Inventory", "Procurement"), d.coverage().notPlanned());
    }

    @Test
    void noPlanAtAllGivesZerosNotErrors() {
        var d = InternalAuditDashboardBuilder.build(null, null, List.of(), universe, List.of(), List.of(), today);
        assertEquals(0, d.plan().completionPct());
        assertEquals(0, d.coverage().plannedPct());
        assertEquals(4, d.coverage().notPlanned().size());
        var empty = InternalAuditDashboardBuilder.build(null, null, List.of(), List.of(), List.of(), List.of(), today);
        assertEquals(0, empty.coverage().auditedPct());
    }

    @Test
    void findingsGroupedBySeverityWithOpenCounts() {
        UUID f = UUID.randomUUID();
        var d = InternalAuditDashboardBuilder.build(2026, "ACTIVE", List.of(), universe, List.of(
                new FindingIn(f, "A", "HIGH", "OPEN", today.minusDays(2), false),
                new FindingIn(f, "B", "high", "CLOSED", null, false),
                new FindingIn(f, "C", "LOW", "IN_PROGRESS", today.plusDays(5), false),
                new FindingIn(f, "D", null, "OPEN", null, false)), List.of(), today);
        assertEquals(3, d.openFindings());
        assertEquals(1, d.findingsPastDue());
        var rows = d.findingsBySeverity();
        assertEquals(List.of("HIGH", "LOW", "UNRATED"), rows.stream().map(SeverityRow::severity).toList());
        assertEquals(1, rows.get(0).open());
        assertEquals(2, rows.get(0).total());
    }

    @Test
    void overdueActionsOldestFirstWithDaysAndLimit() {
        UUID f = UUID.randomUUID();
        var acts = new java.util.ArrayList<ActionIn>();
        for (int i = 1; i <= 12; i++) acts.add(new ActionIn(UUID.randomUUID(), f, "Action " + i, today.minusDays(i)));
        acts.add(new ActionIn(UUID.randomUUID(), f, "Not yet due", today.plusDays(1)));
        var d = InternalAuditDashboardBuilder.build(2026, "ACTIVE", List.of(), universe, List.of(), acts, today);
        assertEquals(12, d.overdueActionCount());
        assertEquals(10, d.overdueActions().size());
        assertEquals(12, d.overdueActions().get(0).daysOverdue());
    }

    @Test
    void repeatFindingsAreCountedAndListed() {
        UUID id = UUID.randomUUID();
        var d = InternalAuditDashboardBuilder.build(2026, "ACTIVE", List.of(), universe, List.of(
                new FindingIn(id, "Again", "HIGH", "OPEN", null, true), new FindingIn(UUID.randomUUID(), "Once", "LOW", "OPEN", null, false)), List.of(), today);
        assertEquals(1, d.repeatFindingCount());
        assertEquals("Again", d.repeatFindings().get(0).title());
    }
}
