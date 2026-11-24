package za.co.handyflow.platform.contracting.domain.model;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.contracting.domain.model.EscalationRules.Absence;
import za.co.handyflow.platform.contracting.domain.model.EscalationRules.Routing;
import za.co.handyflow.platform.contracting.domain.model.EscalationRules.Stage;
import za.co.handyflow.platform.contracting.domain.model.EscalationRules.Thresholds;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class EscalationRulesTest {

    static final Thresholds T = Thresholds.defaults();
    static final LocalDate TODAY = LocalDate.of(2026, 11, 24);
    static final UUID REQ = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    static final UUID A = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    static final UUID B = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
    static final UUID C = UUID.fromString("00000000-0000-0000-0000-0000000000c3");
    static final UUID D = UUID.fromString("00000000-0000-0000-0000-0000000000d4");

    static Absence out(UUID who, UUID cover) { return new Absence(who, TODAY.minusDays(1), TODAY.plusDays(1), cover); }

    // ── thresholds and stages ────────────────────────────────────────────────

    @Test
    void defaultsAreTwentyFourFortyEightSeventyTwo() {
        assertEquals(24, T.reminder1Hours());
        assertEquals(48, T.reminder2Hours());
        assertEquals(72, T.escalateHours());
    }

    @Test
    void thresholdsMustBeInOrderAndWithinAMonth() {
        assertThrows(IllegalArgumentException.class, () -> new Thresholds(0, 48, 72));
        assertThrows(IllegalArgumentException.class, () -> new Thresholds(48, 48, 72));
        assertThrows(IllegalArgumentException.class, () -> new Thresholds(24, 72, 72));
        assertThrows(IllegalArgumentException.class, () -> new Thresholds(24, 48, 721));
        assertDoesNotThrow(() -> new Thresholds(1, 2, 720));
    }

    @Test
    void stageReachedFollowsTheWait() {
        assertEquals(Stage.NONE, EscalationRules.stageReached(0, T));
        assertEquals(Stage.NONE, EscalationRules.stageReached(24 * 60 - 1, T));
        assertEquals(Stage.REMINDER_1, EscalationRules.stageReached(24 * 60, T));
        assertEquals(Stage.REMINDER_1, EscalationRules.stageReached(48 * 60 - 1, T));
        assertEquals(Stage.REMINDER_2, EscalationRules.stageReached(48 * 60, T));
        assertEquals(Stage.ESCALATED, EscalationRules.stageReached(72 * 60, T));
        assertEquals(Stage.ESCALATED, EscalationRules.stageReached(1000 * 60, T));
    }

    @Test
    void eachStepIsSentOnce() {
        assertEquals(Optional.of(Stage.REMINDER_1), EscalationRules.stageToSend(0, 25 * 60, T));
        assertEquals(Optional.empty(), EscalationRules.stageToSend(1, 25 * 60, T));
        assertEquals(Optional.of(Stage.REMINDER_2), EscalationRules.stageToSend(1, 49 * 60, T));
        assertEquals(Optional.of(Stage.ESCALATED), EscalationRules.stageToSend(2, 73 * 60, T));
        assertEquals(Optional.empty(), EscalationRules.stageToSend(3, 500 * 60, T));
        assertEquals(Optional.empty(), EscalationRules.stageToSend(0, 60, T));
    }

    @Test
    void whenSeveralStepsAreOverdueOnlyTheFurthestIsSent() {
        // the engine was switched on, or was down, and the approval is already far past every step
        assertEquals(Optional.of(Stage.ESCALATED), EscalationRules.stageToSend(0, 80 * 60, T));
        assertEquals(Optional.of(Stage.REMINDER_2), EscalationRules.stageToSend(0, 50 * 60, T));
    }

    @Test
    void levelsRoundTrip() {
        for (Stage s : Stage.values()) assertEquals(s, Stage.ofLevel(s.level()));
        assertThrows(IllegalArgumentException.class, () -> Stage.ofLevel(4));
        assertThrows(IllegalArgumentException.class, () -> Stage.ofLevel(-1));
    }

    // ── out of office ────────────────────────────────────────────────────────

    @Test
    void someoneWhoIsInIsTheirOwnCover() {
        assertEquals(Optional.of(A), EscalationRules.presentOrCover(A, List.of(), TODAY));
        assertEquals(Optional.of(A), EscalationRules.presentOrCover(A, List.of(out(B, C)), TODAY));
    }

    @Test
    void someoneWhoIsOutIsCoveredByTheirDelegate() {
        assertEquals(Optional.of(C), EscalationRules.presentOrCover(A, List.of(out(A, C)), TODAY));
    }

    @Test
    void outWithNoDelegateMeansNoOneCan() {
        assertEquals(Optional.empty(), EscalationRules.presentOrCover(A, List.of(out(A, null)), TODAY));
    }

    @Test
    void anAbsenceCoversBothEndDaysAndNothingOutsideThem() {
        Absence a = new Absence(A, TODAY, TODAY.plusDays(2), C);
        assertEquals(Optional.of(C), EscalationRules.presentOrCover(A, List.of(a), TODAY));
        assertEquals(Optional.of(C), EscalationRules.presentOrCover(A, List.of(a), TODAY.plusDays(2)));
        assertEquals(Optional.of(A), EscalationRules.presentOrCover(A, List.of(a), TODAY.minusDays(1)));
        assertEquals(Optional.of(A), EscalationRules.presentOrCover(A, List.of(a), TODAY.plusDays(3)));
    }

    @Test
    void aDelegateWhoIsAlsoOutPassesItOn() {
        assertEquals(Optional.of(D), EscalationRules.presentOrCover(A, List.of(out(A, C), out(C, D)), TODAY));
    }

    @Test
    void aLoopOfDelegatesEndsWithNoOne() {
        assertEquals(Optional.empty(), EscalationRules.presentOrCover(A, List.of(out(A, C), out(C, A)), TODAY));
    }

    @Test
    void aVeryLongChainStopsRatherThanRunningOn() {
        UUID e = UUID.randomUUID(), f = UUID.randomUUID();
        assertEquals(Optional.empty(), EscalationRules.presentOrCover(A, List.of(out(A, B), out(B, C), out(C, D), out(D, e), out(e, f)), TODAY));
    }

    // ── who a reminder goes to ───────────────────────────────────────────────

    @Test
    void reminderGoesToEveryoneWhoCanApproveExceptTheRequester() {
        Routing r = EscalationRules.routeReminder(null, List.of(REQ, A, B), REQ, List.of(), TODAY);
        assertEquals(List.of(A, B), r.people());
        assertFalse(r.fallbackToEscalationContacts());
    }

    @Test
    void reminderGoesToTheAssignedApproverFirst() {
        Routing r = EscalationRules.routeReminder(B, List.of(A, B, C), REQ, List.of(), TODAY);
        assertEquals(List.of(B), r.people());
    }

    @Test
    void anAssigneeWhoIsOutIsReplacedByTheirDelegate() {
        Routing r = EscalationRules.routeReminder(B, List.of(A, B, C), REQ, List.of(out(B, D)), TODAY);
        assertEquals(List.of(D), r.people());
    }

    @Test
    void anAssigneeWhoIsOutWithNoCoverFallsBackToEveryoneElseWhoCanApprove() {
        Routing r = EscalationRules.routeReminder(B, List.of(A, B, C), REQ, List.of(out(B, null)), TODAY);
        assertEquals(List.of(A, C), r.people());
        assertFalse(r.fallbackToEscalationContacts());
    }

    @Test
    void peopleWhoAreOutAreSkippedOrCoveredInThePool() {
        Routing r = EscalationRules.routeReminder(null, List.of(A, B, C), REQ, List.of(out(A, null), out(B, D)), TODAY);
        assertEquals(List.of(D, C), r.people());
    }

    @Test
    void whenNoOneCanBeReachedTheReminderFallsBackToTheEscalationContacts() {
        Routing r = EscalationRules.routeReminder(null, List.of(A, B), REQ, List.of(out(A, null), out(B, null)), TODAY);
        assertTrue(r.people().isEmpty());
        assertTrue(r.fallbackToEscalationContacts());
        Routing onlyRequester = EscalationRules.routeReminder(null, List.of(REQ), REQ, List.of(), TODAY);
        assertTrue(onlyRequester.fallbackToEscalationContacts());
    }

    @Test
    void aDelegateWhoIsTheRequesterIsNotUsed() {
        Routing r = EscalationRules.routeReminder(null, List.of(A), REQ, List.of(out(A, REQ)), TODAY);
        assertTrue(r.people().isEmpty());
        assertTrue(r.fallbackToEscalationContacts());
    }

    @Test
    void nobodyIsToldTwice() {
        Routing r = EscalationRules.routeReminder(null, List.of(A, B, C), REQ, List.of(out(B, A), out(C, A)), TODAY);
        assertEquals(List.of(A), r.people());
    }

    // ── who an escalation goes to ────────────────────────────────────────────

    @Test
    void escalationGoesToTheNamedContactsOtherwiseTheAdministrators() {
        assertEquals(List.of(C), EscalationRules.routeEscalation(List.of(C), List.of(A, B), REQ, List.of(), TODAY));
        assertEquals(List.of(A, B), EscalationRules.routeEscalation(List.of(), List.of(A, B), REQ, List.of(), TODAY));
        assertEquals(List.of(A, B), EscalationRules.routeEscalation(null, List.of(A, B), REQ, List.of(), TODAY));
    }

    @Test
    void escalationNeverGoesToTheRequesterAndCoversForAbsentContacts() {
        assertEquals(List.of(A), EscalationRules.routeEscalation(List.of(REQ, A), List.of(B), REQ, List.of(), TODAY));
        assertEquals(List.of(D), EscalationRules.routeEscalation(List.of(C), List.of(A), REQ, List.of(out(C, D)), TODAY));
    }

    @Test
    void escalationFallsBackToTheAdministratorsWhenNoNamedContactCanBeReached() {
        // the only named contact is away with no cover
        assertEquals(List.of(A), EscalationRules.routeEscalation(List.of(C), List.of(A), REQ, List.of(out(C, null)), TODAY));
        // the only named contact is the person who asked
        assertEquals(List.of(A), EscalationRules.routeEscalation(List.of(REQ), List.of(A), REQ, List.of(), TODAY));
        // nobody at all can be reached
        assertEquals(List.of(), EscalationRules.routeEscalation(List.of(C), List.of(A), REQ, List.of(out(C, null), out(A, null)), TODAY));
    }

    // ── stored forms ─────────────────────────────────────────────────────────

    @Test
    void idsRoundTripAndIgnoreBlanksAndRepeats() {
        assertEquals(List.of(A, B), EscalationRules.parseIds(A + ", " + B + ",," + A));
        assertEquals(List.of(), EscalationRules.parseIds(null));
        assertEquals(List.of(), EscalationRules.parseIds("  "));
        assertEquals(A + "," + B, EscalationRules.joinIds(List.of(A, B, A)));
        assertEquals("", EscalationRules.joinIds(null));
        assertThrows(IllegalArgumentException.class, () -> EscalationRules.parseIds("not-an-id"));
    }

    @Test
    void daysRoundTripInWeekOrder() {
        Set<DayOfWeek> d = EscalationRules.parseDays("fri, MON,Tuesday");
        assertEquals(Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.FRIDAY), d);
        assertEquals("MON,TUE,FRI", EscalationRules.joinDays(d));
        assertEquals("MON,TUE,WED,THU,FRI", EscalationRules.joinDays(ApprovalWorkingTime.MON_TO_FRI));
        assertThrows(IllegalArgumentException.class, () -> EscalationRules.parseDays("funday"));
        assertThrows(IllegalArgumentException.class, () -> EscalationRules.parseDays(""));
        assertThrows(IllegalArgumentException.class, () -> EscalationRules.parseDays(" , "));
    }
}
