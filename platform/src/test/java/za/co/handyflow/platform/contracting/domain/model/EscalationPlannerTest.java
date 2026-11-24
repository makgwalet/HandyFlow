package za.co.handyflow.platform.contracting.domain.model;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.contracting.domain.model.EscalationPlanner.Context;
import za.co.handyflow.platform.contracting.domain.model.EscalationPlanner.Plan;
import za.co.handyflow.platform.contracting.domain.model.EscalationPlanner.Waiting;
import za.co.handyflow.platform.contracting.domain.model.EscalationRules.Absence;
import za.co.handyflow.platform.contracting.domain.model.EscalationRules.Stage;
import za.co.handyflow.platform.contracting.domain.model.EscalationRules.Thresholds;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** The whole decision for one waiting approval, from the working clock to the list of people to tell. */
class EscalationPlannerTest {

    static final ZoneId ZONE = ZoneId.of("Africa/Johannesburg");
    static final ApprovalWorkingTime CALENDAR = ApprovalWorkingTime.calendar(ZONE);
    static final ApprovalWorkingTime WEEK = ApprovalWorkingTime.businessHours(ZONE, LocalTime.of(8, 0), LocalTime.of(17, 0), ApprovalWorkingTime.MON_TO_FRI, Set.of());
    static final UUID REQ = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    static final UUID A = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    static final UUID B = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
    static final UUID ADMIN = UUID.fromString("00000000-0000-0000-0000-0000000000ad");
    static final UUID NAMED = UUID.fromString("00000000-0000-0000-0000-0000000000c3");

    static Instant at(String local) { return LocalDateTime.parse(local).atZone(ZONE).toInstant(); }

    static Context ctx(ApprovalWorkingTime clock, Instant now, Instant enabledSince, List<UUID> contacts, List<Absence> absences) {
        return new Context(Thresholds.defaults(), clock, enabledSince, List.of(REQ, A, B), contacts, List.of(ADMIN),
                absences, now.atZone(ZONE).toLocalDate(), now);
    }

    static Waiting waiting(String requested, int level, UUID assignedTo) { return new Waiting(REQ, assignedTo, level, at(requested)); }

    @Test
    void nothingIsDueBeforeTheFirstThreshold() {
        assertTrue(EscalationPlanner.plan(waiting("2026-11-23T08:00", 0, null), ctx(CALENDAR, at("2026-11-24T07:59"), null, List.of(), List.of())).isEmpty());
    }

    @Test
    void theFirstReminderGoesToEveryoneWhoCanApproveExceptTheRequester() {
        Plan p = EscalationPlanner.plan(waiting("2026-11-23T08:00", 0, null), ctx(CALENDAR, at("2026-11-24T08:00"), null, List.of(), List.of())).orElseThrow();
        assertEquals(Stage.REMINDER_1, p.stage());
        assertEquals(List.of(A, B), p.recipients());
        assertFalse(p.tellRequester());
        assertFalse(p.fellBackToEscalation());
        assertFalse(p.skippedEarlierSteps());
        assertEquals(1440, p.waitedMinutes());
    }

    @Test
    void anAssignedApproverIsRemindedInsteadOfEveryone() {
        Plan p = EscalationPlanner.plan(waiting("2026-11-23T08:00", 0, B), ctx(CALENDAR, at("2026-11-24T08:00"), null, List.of(), List.of())).orElseThrow();
        assertEquals(List.of(B), p.recipients());
    }

    @Test
    void theSecondReminderFollowsTheFirst() {
        Plan p = EscalationPlanner.plan(waiting("2026-11-23T08:00", 1, null), ctx(CALENDAR, at("2026-11-25T08:00"), null, List.of(), List.of())).orElseThrow();
        assertEquals(Stage.REMINDER_2, p.stage());
        assertFalse(p.skippedEarlierSteps());
    }

    @Test
    void escalationGoesToTheAdministratorsAndTellsTheRequester() {
        Plan p = EscalationPlanner.plan(waiting("2026-11-23T08:00", 2, null), ctx(CALENDAR, at("2026-11-26T08:00"), null, List.of(), List.of())).orElseThrow();
        assertEquals(Stage.ESCALATED, p.stage());
        assertEquals(List.of(ADMIN), p.recipients());
        assertTrue(p.tellRequester());
    }

    @Test
    void escalationGoesToTheNamedContactsWhenThereAreAny() {
        Plan p = EscalationPlanner.plan(waiting("2026-11-23T08:00", 2, null), ctx(CALENDAR, at("2026-11-26T08:00"), null, List.of(NAMED), List.of())).orElseThrow();
        assertEquals(List.of(NAMED), p.recipients());
    }

    @Test
    void anAlreadyEscalatedApprovalIsNeverEscalatedAgain() {
        assertTrue(EscalationPlanner.plan(waiting("2026-11-01T08:00", 3, null), ctx(CALENDAR, at("2026-11-26T08:00"), null, List.of(), List.of())).isEmpty());
    }

    @Test
    void aStepAlreadySentIsNotSentAgain() {
        assertTrue(EscalationPlanner.plan(waiting("2026-11-23T08:00", 1, null), ctx(CALENDAR, at("2026-11-24T20:00"), null, List.of(), List.of())).isEmpty());
    }

    @Test
    void whenSeveralStepsAreOverdueOnlyTheFurthestIsSentAndItSaysSo() {
        Plan p = EscalationPlanner.plan(waiting("2026-11-01T08:00", 0, null), ctx(CALENDAR, at("2026-11-24T08:00"), null, List.of(), List.of())).orElseThrow();
        assertEquals(Stage.ESCALATED, p.stage());
        assertTrue(p.skippedEarlierSteps());
    }

    @Test
    void timeBeforeEscalationWasSwitchedOnDoesNotCount() {
        // asked three weeks ago, switched on this morning: the clock starts this morning
        Instant on = at("2026-11-24T08:00");
        Waiting old = waiting("2026-11-03T08:00", 0, null);
        assertTrue(EscalationPlanner.plan(old, ctx(CALENDAR, at("2026-11-24T20:00"), on, List.of(), List.of())).isEmpty());
        Plan p = EscalationPlanner.plan(old, ctx(CALENDAR, at("2026-11-25T08:00"), on, List.of(), List.of())).orElseThrow();
        assertEquals(Stage.REMINDER_1, p.stage());
        assertEquals(1440, p.waitedMinutes());
    }

    @Test
    void switchingOnBeforeTheRequestWasMadeChangesNothing() {
        Plan p = EscalationPlanner.plan(waiting("2026-11-23T08:00", 0, null),
                ctx(CALENDAR, at("2026-11-24T08:00"), at("2026-11-01T08:00"), List.of(), List.of())).orElseThrow();
        assertEquals(1440, p.waitedMinutes());
    }

    @Test
    void theBusinessClockMakesAWeekendWaitCountForNothing() {
        // asked Friday 16:00. By Monday 08:00 only one working hour has passed.
        assertTrue(EscalationPlanner.plan(waiting("2026-11-27T16:00", 0, null), ctx(WEEK, at("2026-11-30T08:00"), null, List.of(), List.of())).isEmpty());
        // 24 working hours: 60 min on Friday, 540 on Monday, 540 on Tuesday, then 300 more, which ends at 13:00 on Wednesday
        assertTrue(EscalationPlanner.plan(waiting("2026-11-27T16:00", 0, null), ctx(WEEK, at("2026-12-02T12:59"), null, List.of(), List.of())).isEmpty());
        Plan p = EscalationPlanner.plan(waiting("2026-11-27T16:00", 0, null), ctx(WEEK, at("2026-12-02T13:00"), null, List.of(), List.of())).orElseThrow();
        assertEquals(Stage.REMINDER_1, p.stage());
        assertEquals(24 * 60, p.waitedMinutes());
    }

    @Test
    void anApproverWhoIsOutIsReplacedByTheirCover() {
        Absence out = new Absence(A, LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 30), B);
        Plan p = EscalationPlanner.plan(waiting("2026-11-23T08:00", 0, A), ctx(CALENDAR, at("2026-11-24T08:00"), null, List.of(), List.of(out))).orElseThrow();
        assertEquals(List.of(B), p.recipients());
    }

    @Test
    void whenEveryApproverIsOutTheReminderGoesToTheEscalationContactsInstead() {
        List<Absence> allOut = List.of(new Absence(A, LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 30), null),
                new Absence(B, LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 30), null));
        Plan p = EscalationPlanner.plan(waiting("2026-11-23T08:00", 0, null), ctx(CALENDAR, at("2026-11-24T08:00"), null, List.of(NAMED), allOut)).orElseThrow();
        assertEquals(Stage.REMINDER_1, p.stage());
        assertEquals(List.of(NAMED), p.recipients());
        assertTrue(p.fellBackToEscalation());
        assertFalse(p.tellRequester());
    }

    @Test
    void whenNoOneAtAllCanBeReachedThePlanSaysSoRatherThanSilentlyDoingNothing() {
        List<Absence> allOut = List.of(new Absence(A, LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 30), null),
                new Absence(B, LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 30), null),
                new Absence(ADMIN, LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 30), null));
        Plan p = EscalationPlanner.plan(waiting("2026-11-23T08:00", 0, null), ctx(CALENDAR, at("2026-11-24T08:00"), null, List.of(), allOut)).orElseThrow();
        assertTrue(p.noOneToTell());
        assertTrue(p.fellBackToEscalation());
        assertEquals(Optional.of(Stage.REMINDER_1), Optional.of(p.stage()));
    }

    @Test
    void theRequesterIsNeverOneOfThePeopleToTell() {
        for (int level = 0; level < 3; level++) {
            Plan p = EscalationPlanner.plan(waiting("2026-11-01T08:00", level, REQ), ctx(CALENDAR, at("2026-11-24T08:00"), null, List.of(REQ), List.of())).orElseThrow();
            assertFalse(p.recipients().contains(REQ), "level " + level);
        }
    }

    @Test
    void theCheapPreCheckAgreesWithThePlan() {
        Waiting w = waiting("2026-11-23T08:00", 0, null);
        for (String now : new String[]{"2026-11-23T09:00", "2026-11-24T07:59", "2026-11-24T08:00", "2026-11-25T08:00", "2026-11-26T08:00", "2026-12-20T08:00"}) {
            Context c = ctx(CALENDAR, at(now), null, List.of(), List.of());
            assertEquals(EscalationPlanner.plan(w, c).map(Plan::stage),
                    EscalationPlanner.stageDue(w, c.thresholds(), c.clock(), c.enabledSince(), c.now()), now);
        }
    }
}
