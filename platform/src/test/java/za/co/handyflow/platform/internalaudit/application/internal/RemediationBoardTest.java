package za.co.handyflow.platform.internalaudit.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.internalaudit.application.internal.RemediationBoard.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RemediationBoardTest {
    static final LocalDate TODAY = LocalDate.of(2026, 10, 28);
    UUID eng = UUID.randomUUID(), sarah = UUID.randomUUID(), mike = UUID.randomUUID();
    List<EngagementIn> engagements = List.of(new EngagementIn(eng, "Financial reporting audit", "Finance"));
    Map<UUID, String> names = Map.of(sarah, "Sarah Johnson", mike, "Mike Brown");

    private FindingIn finding(UUID id, String sev, String status, LocalDate due) {
        return new FindingIn(id, eng, "F-" + id.toString().substring(0, 4), "d", "r", null, sev, status, sarah, due, Instant.parse("2026-09-01T00:00:00Z"), false);
    }
    private ActionIn action(UUID finding, String status, LocalDate due) { return new ActionIn(UUID.randomUUID(), finding, "Fix it", mike, due, status); }

    @Test
    void worstFindingsComeFirstAndSettledOnesLast() {
        UUID low = UUID.randomUUID(), crit = UUID.randomUUID(), closedCrit = UUID.randomUUID(), highLate = UUID.randomUUID(), highSoon = UUID.randomUUID();
        Board b = RemediationBoard.build(List.of(finding(low, "LOW", "OPEN", null), finding(closedCrit, "CRITICAL", "CLOSED", null), finding(highSoon, "HIGH", "OPEN", TODAY.plusDays(9)),
                finding(crit, "CRITICAL", "IN_PROGRESS", TODAY.plusDays(3)), finding(highLate, "HIGH", "OPEN", TODAY.minusDays(2))), engagements, List.of(), names, TODAY);
        assertEquals(List.of(crit, highLate, highSoon, low, closedCrit), b.findings().stream().map(Row::id).toList());
    }

    @Test
    void remediationCountsValidatedActionsAndLeavesCancelledOnesOut() {
        UUID f = UUID.randomUUID();
        Board b = RemediationBoard.build(List.of(finding(f, "HIGH", "OPEN", null)), engagements, List.of(
                action(f, "VALIDATED", TODAY.minusDays(5)), action(f, "COMPLETED", TODAY.minusDays(1)), action(f, "OPEN", TODAY.plusDays(4)), action(f, "CANCELLED", TODAY.minusDays(30))), names, TODAY);
        Row r = b.findings().get(0);
        assertEquals(3, r.actions());
        assertEquals(1, r.actionsDone());
        assertEquals(1, r.actionsAwaiting());
        assertEquals(1, r.actionsOverdue());      // completed but not yet validated, and past its date
        assertEquals(33, r.remediationPct());
        assertEquals("Finance", r.area());
        assertEquals("Sarah Johnson", r.owner());
    }

    @Test
    void aFindingWithNoActionsHasNoPercentageAndIsCounted() {
        UUID open = UUID.randomUUID(), closed = UUID.randomUUID(), covered = UUID.randomUUID();
        Board b = RemediationBoard.build(List.of(finding(open, "HIGH", "OPEN", null), finding(closed, "HIGH", "CLOSED", null), finding(covered, "LOW", "OPEN", null)),
                engagements, List.of(action(covered, "OPEN", TODAY.plusDays(1))), names, TODAY);
        assertEquals(1, b.summary().openWithoutAction());      // the closed one does not count, nor the one with an action
        assertEquals(0, b.findings().stream().filter(r -> r.id().equals(open)).findFirst().get().remediationPct());
    }

    @Test
    void summaryCountsByStatusAndOpenSeverityAndPastDue() {
        Board b = RemediationBoard.build(List.of(
                finding(UUID.randomUUID(), "CRITICAL", "OPEN", TODAY.minusDays(1)), finding(UUID.randomUUID(), "HIGH", "IN_PROGRESS", TODAY),
                finding(UUID.randomUUID(), "MEDIUM", "RESOLVED", TODAY.minusDays(10)), finding(UUID.randomUUID(), "LOW", "CLOSED", null),
                finding(UUID.randomUUID(), "high", "OPEN", null)), engagements, List.of(), names, TODAY);
        Summary s = b.summary();
        assertEquals(5, s.findings());
        assertEquals(2, s.open());
        assertEquals(1, s.inProgress());
        assertEquals(1, s.resolved());
        assertEquals(1, s.closed());
        assertEquals(1, s.openCritical());
        assertEquals(2, s.openHigh());          // case does not matter
        assertEquals(0, s.openMedium());        // the medium one is resolved, not open
        assertEquals(1, s.pastDue());           // due today is not past due; a resolved one is not chased
    }

    @Test
    void summaryCountsActionsAndTheirCompletionRate() {
        UUID f = UUID.randomUUID();
        Board b = RemediationBoard.build(List.of(finding(f, "HIGH", "OPEN", null)), engagements, List.of(
                action(f, "VALIDATED", TODAY), action(f, "VALIDATED", TODAY), action(f, "COMPLETED", TODAY.plusDays(2)), action(f, "OPEN", TODAY.minusDays(3)), action(f, "CANCELLED", TODAY)), names, TODAY);
        Summary s = b.summary();
        assertEquals(4, s.actions());
        assertEquals(1, s.actionsOpen());
        assertEquals(1, s.actionsAwaiting());
        assertEquals(2, s.actionsValidated());
        assertEquals(1, s.actionsOverdue());
        assertEquals(50, s.remediationPct());
    }

    @Test
    void actionsListOverdueFirstLongestLateFirst() {
        UUID f = UUID.randomUUID();
        ActionIn soon = action(f, "OPEN", TODAY.plusDays(2)), late5 = action(f, "OPEN", TODAY.minusDays(5)), late20 = action(f, "COMPLETED", TODAY.minusDays(20)), done = action(f, "VALIDATED", TODAY.minusDays(40));
        Board b = RemediationBoard.build(List.of(finding(f, "HIGH", "OPEN", null)), engagements, List.of(soon, done, late5, late20), names, TODAY);
        assertEquals(List.of(late20.id(), late5.id(), soon.id(), done.id()), b.actions().stream().map(ActionRow::id).toList());
        assertEquals(20, b.actions().get(0).daysOverdue());
        assertFalse(b.actions().get(3).overdue());      // validated is never overdue
        assertEquals("Mike Brown", b.actions().get(0).owner());
    }

    @Test
    void anActionForAFindingThatIsNotThereIsLeftOut() {
        Board b = RemediationBoard.build(List.of(), engagements, List.of(action(UUID.randomUUID(), "OPEN", TODAY)), names, TODAY);
        assertTrue(b.actions().isEmpty());
        assertEquals(0, b.summary().actions());
    }

    @Test
    void unknownOwnersAndEngagementsAreNullNotErrors() {
        UUID f = UUID.randomUUID();
        FindingIn orphan = new FindingIn(f, UUID.randomUUID(), "x", "d", null, null, null, "OPEN", UUID.randomUUID(), null, null, true);
        Row r = RemediationBoard.build(List.of(orphan), engagements, List.of(), names, TODAY).findings().get(0);
        assertNull(r.owner());
        assertNull(r.engagement());
        assertTrue(r.repeat());
    }
}
