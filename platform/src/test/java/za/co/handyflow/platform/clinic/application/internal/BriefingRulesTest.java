package za.co.handyflow.platform.clinic.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.clinic.application.internal.BriefingRules.Appt;
import za.co.handyflow.platform.clinic.application.internal.BriefingRules.Visit;
import za.co.handyflow.platform.clinic.dto.PatientBriefing.Alert;
import za.co.handyflow.platform.clinic.dto.PatientBriefing.Recall;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BriefingRulesTest {

    private static final java.time.ZoneId ZONE = BriefingRules.CLINIC_ZONE;
    private static final Instant NOW = Instant.parse("2026-10-08T08:00:00Z");   // 10:00 in Johannesburg
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 8);

    private static Visit visit(String at, String status, Integer followUp) {
        return new Visit(UUID.randomUUID(), Instant.parse(at), status, followUp, null);
    }
    private static Appt appt(String at, String status) { return new Appt(UUID.randomUUID(), Instant.parse(at), status); }

    @Test
    void onlyFinishedVisitsCountAndTheNewestComesFirst() {
        var old = visit("2026-05-01T08:00:00Z", "SIGNED", null);
        var newer = visit("2026-09-01T08:00:00Z", "LOCKED", null);
        var done = visit("2026-08-01T08:00:00Z", "DOCTOR_COMPLETED", null);
        var draft = visit("2026-10-01T08:00:00Z", "DRAFT", null);
        var abandoned = visit("2026-10-02T08:00:00Z", "ABANDONED", null);
        var out = BriefingRules.finishedNewestFirst(List.of(old, draft, newer, abandoned, done));
        assertEquals(List.of(newer, done, old), out);
    }

    @Test
    void daysSinceCountsClinicCalendarDaysNotHours() {
        // 23:30 SAST the day before is one calendar day ago even though it is only 10.5 hours earlier.
        assertEquals(1L, BriefingRules.daysSince(Instant.parse("2026-10-07T21:30:00Z"), NOW, ZONE));
        assertEquals(0L, BriefingRules.daysSince(Instant.parse("2026-10-08T05:00:00Z"), NOW, ZONE));
        assertEquals(146L, BriefingRules.daysSince(Instant.parse("2026-05-15T08:00:00Z"), NOW, ZONE));
        assertEquals(0L, BriefingRules.daysSince(Instant.parse("2026-10-09T08:00:00Z"), NOW, ZONE));   // never negative
    }

    @Test
    void nextAppointmentIsTheEarliestUpcomingOneThatIsStillOn() {
        var past = appt("2026-10-01T08:00:00Z", "SCHEDULED");
        var cancelled = appt("2026-10-09T08:00:00Z", "CANCELLED");
        var later = appt("2026-10-20T08:00:00Z", "CONFIRMED");
        var soon = appt("2026-10-10T08:00:00Z", "SCHEDULED");
        var inProgress = appt("2026-10-08T09:00:00Z", "IN_PROGRESS");
        assertEquals(soon, BriefingRules.nextAppointment(List.of(past, cancelled, later, soon, inProgress), NOW, ZONE).orElseThrow());
        assertTrue(BriefingRules.nextAppointment(List.of(past, cancelled), NOW, ZONE).isEmpty());
    }

    @Test
    void anAppointmentEarlierTodayThatHasNotStartedIsStillNext() {
        var late = appt("2026-10-08T05:53:00Z", "SCHEDULED");           // 07:53 in Johannesburg, two hours ago
        var later = appt("2026-10-10T08:00:00Z", "SCHEDULED");
        assertEquals(late, BriefingRules.nextAppointment(List.of(later, late), NOW, ZONE).orElseThrow());
        var arrived = appt("2026-10-08T05:53:00Z", "CHECKED_IN");
        assertEquals(arrived, BriefingRules.nextAppointment(List.of(arrived), NOW, ZONE).orElseThrow());
    }

    @Test
    void yesterdaysUnattendedBookingIsNotNextAndAStartedOneIsNot() {
        var yesterday = appt("2026-10-07T08:00:00Z", "SCHEDULED");
        var started = appt("2026-10-08T05:00:00Z", "IN_PROGRESS");
        var done = appt("2026-10-08T05:00:00Z", "COMPLETED");
        assertTrue(BriefingRules.nextAppointment(List.of(yesterday, started, done), NOW, ZONE).isEmpty());
    }

    @Test
    void aPromisedFollowUpIsReportedWithItsOverdueDays() {
        var v = visit("2026-09-24T08:00:00Z", "SIGNED", 7);          // due 1 Oct, 7 days overdue on 8 Oct
        Recall r = BriefingRules.recall(v, List.of(), TODAY, ZONE);
        assertEquals(LocalDate.of(2026, 10, 1), r.dueDate());
        assertEquals(7, r.overdueDays());
        assertTrue(r.due());
    }

    @Test
    void aFollowUpNotYetDueIsStillShownButNotOverdue() {
        var v = visit("2026-10-05T08:00:00Z", "SIGNED", 14);
        Recall r = BriefingRules.recall(v, List.of(), TODAY, ZONE);
        assertFalse(r.due());
        assertEquals(0, r.overdueDays());
    }

    @Test
    void aBookedFollowUpClearsTheRecallButCancelledOrNoShowDoNot() {
        var v = visit("2026-09-24T08:00:00Z", "SIGNED", 7);
        assertNull(BriefingRules.recall(v, List.of(appt("2026-10-15T08:00:00Z", "SCHEDULED")), TODAY, ZONE));
        assertNotNull(BriefingRules.recall(v, List.of(appt("2026-10-15T08:00:00Z", "CANCELLED"), appt("2026-10-02T08:00:00Z", "NO_SHOW")), TODAY, ZONE));
    }

    @Test
    void theVisitsOwnAppointmentDoesNotCountAsTheFollowUp() {
        UUID own = UUID.randomUUID();
        var v = new Visit(UUID.randomUUID(), Instant.parse("2026-09-24T08:00:00Z"), "SIGNED", 7, own);
        var ownAppt = new Appt(own, Instant.parse("2026-09-24T08:30:00Z"), "COMPLETED");
        assertNotNull(BriefingRules.recall(v, List.of(ownAppt), TODAY, ZONE));
    }

    @Test
    void noFollowUpSetMeansNoRecall() {
        assertNull(BriefingRules.recall(visit("2026-09-24T08:00:00Z", "SIGNED", null), List.of(), TODAY, ZONE));
        assertNull(BriefingRules.recall(null, List.of(), TODAY, ZONE));
    }

    @Test
    void alertsListTheSeriousThingsFirstAndStaySilentWhenAllIsWell() {
        assertTrue(BriefingRules.alerts(List.of(), 0, 0, null, false, 3).isEmpty());

        List<Alert> a = BriefingRules.alerts(List.of("Penicillin", "Latex"), 1, 2, new Recall(TODAY.minusDays(3), 3, true), true, 0);
        assertEquals(List.of("SEVERE_ALLERGY", "CRITICAL_LAB", "ABNORMAL_LAB", "RECALL_OVERDUE", "OPEN_DRAFT", "FIRST_VISIT"),
                a.stream().map(Alert::code).toList());
        assertEquals("Severe allergy: Penicillin, Latex", a.get(0).message());
        assertEquals("DANGER", a.get(0).severity());
        assertEquals("1 critical lab result not yet reviewed", a.get(1).message());
        assertEquals("2 abnormal lab results not yet reviewed", a.get(2).message());
        assertEquals("Follow-up is 3 days overdue", a.get(3).message());
    }

    @Test
    void aFollowUpThatIsDueTodayIsNotAnOverdueAlert() {
        assertTrue(BriefingRules.alerts(List.of(), 0, 0, new Recall(TODAY, 0, true), false, 2).isEmpty());
    }
}
