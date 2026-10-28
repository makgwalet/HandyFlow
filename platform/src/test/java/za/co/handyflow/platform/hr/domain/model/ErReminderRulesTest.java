package za.co.handyflow.platform.hr.domain.model;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.hr.domain.model.ErReminderRules.When;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;

class ErReminderRulesTest {

    private static final LocalDate DUE = LocalDate.of(2026, 11, 10);

    @Test
    void oneReminderTheDayBeforeThenNothingUntilItIsLate() {
        assertEquals(When.NONE, ErReminderRules.when(DUE, DUE.minusDays(3)));
        assertEquals(When.NONE, ErReminderRules.when(DUE, DUE.minusDays(2)));
        assertEquals(When.TOMORROW, ErReminderRules.when(DUE, DUE.minusDays(1)));
        assertEquals(When.NONE, ErReminderRules.when(DUE, DUE));
    }

    @Test
    void lateFromTheDayAfterThenEverySeventhDay() {
        assertEquals(When.LATE, ErReminderRules.when(DUE, DUE.plusDays(1)));
        for (int i = 2; i <= 7; i++) assertEquals(When.NONE, ErReminderRules.when(DUE, DUE.plusDays(i)), "day " + i);
        assertEquals(When.LATE, ErReminderRules.when(DUE, DUE.plusDays(8)));
        assertEquals(When.NONE, ErReminderRules.when(DUE, DUE.plusDays(9)));
        assertEquals(When.LATE, ErReminderRules.when(DUE, DUE.plusDays(15)));
    }

    @Test
    void noDateMeansNoReminder() {
        assertEquals(When.NONE, ErReminderRules.when(null, DUE));
    }

    @Test
    void stepsAreNamedPlainly() {
        assertEquals("Meeting", ErReminderRules.step("Meeting"));
        assertEquals("Grievance response", ErReminderRules.step("Response due"));
        assertEquals("Telling the employee the decision", ErReminderRules.step("Tell the employee"));
        assertEquals("End of the appeal period", ErReminderRules.step("Appeal period ends"));
        assertEquals("Case step", ErReminderRules.step("Something new"));
        assertEquals("Case step", ErReminderRules.step(null));
    }

    @Test
    void wordsCarryTheCaseNumberAndStepOnly() {
        assertEquals("ER-2026-007: meeting is due tomorrow", ErReminderRules.title("ER-2026-007", "Meeting", When.TOMORROW));
        assertEquals("ER-2026-007: grievance response is late", ErReminderRules.title("ER-2026-007", "Response due", When.LATE));
        assertEquals("Meeting for case ER-2026-007 is due on 2026-11-10. Open the case in Employee relations.",
                ErReminderRules.message("ER-2026-007", "Meeting", DUE, When.TOMORROW, DUE.minusDays(1)));
        assertEquals("Grievance response for case ER-2026-007 was due on 2026-11-10 and is 1 day late. Open the case in Employee relations.",
                ErReminderRules.message("ER-2026-007", "Response due", DUE, When.LATE, DUE.plusDays(1)));
        assertTrue(ErReminderRules.message("ER-2026-007", "Response due", DUE, When.LATE, DUE.plusDays(8)).contains("is 8 days late"));
    }

    // ── The hearing ──────────────────────────────────────────────────────────

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 9);

    @Test
    void aHearingIsRemindedTheDayBeforeOnlyWhenItIsNoticedAndNotYetHeld() {
        assertTrue(ErReminderRules.hearingTomorrow("NOTICE_ISSUED", TODAY.plusDays(1), null, TODAY));
        assertFalse(ErReminderRules.hearingTomorrow("NOTICE_ISSUED", TODAY.plusDays(2), null, TODAY));
        assertFalse(ErReminderRules.hearingTomorrow("NOTICE_ISSUED", TODAY, null, TODAY));
        assertFalse(ErReminderRules.hearingTomorrow("NOTICE_ISSUED", TODAY.plusDays(1), TODAY, TODAY));
        assertFalse(ErReminderRules.hearingTomorrow("HEARING_HELD", TODAY.plusDays(1), null, TODAY));
        assertFalse(ErReminderRules.hearingTomorrow("REPORTED", TODAY.plusDays(1), null, TODAY));
        assertFalse(ErReminderRules.hearingTomorrow("NOTICE_ISSUED", null, null, TODAY));
    }

    @Test
    void eachPersonGetsWordsForTheirPartAndNoneNamesTheMatter() {
        LocalDate d = TODAY.plusDays(1);
        String chair = ErReminderRules.hearingMessage(ErReminderRules.Role.CHAIR, "ER-7", d, LocalTime.of(9, 30));
        String initiator = ErReminderRules.hearingMessage(ErReminderRules.Role.INITIATOR, "ER-7", d, null);
        String employee = ErReminderRules.hearingMessage(ErReminderRules.Role.EMPLOYEE, "ER-7", d, LocalTime.of(9, 30));
        assertTrue(chair.contains("You chair") && chair.contains("2026-11-10 at 09:30"));
        assertTrue(initiator.contains("You present the case") && !initiator.contains("2026-11-10 at"));
        assertTrue(employee.contains("Your hearing") && employee.contains("tell HR today"));
        for (String m : new String[]{chair, initiator, employee}) assertTrue(m.contains("ER-7") && !m.toLowerCase().contains("dismiss") && !m.toLowerCase().contains("theft"));
    }

    // ── Suspension and review ────────────────────────────────────────────────

    @Test
    void aSuspensionWithAnEndDateIsRemindedTheDayBeforeAndNotAfter() {
        LocalDate from = TODAY.minusDays(10);
        assertEquals(ErReminderRules.Suspension.ENDS_TOMORROW, ErReminderRules.suspension(from, TODAY.plusDays(1), TODAY));
        assertEquals(ErReminderRules.Suspension.NONE, ErReminderRules.suspension(from, TODAY.plusDays(2), TODAY));
        assertEquals(ErReminderRules.Suspension.NONE, ErReminderRules.suspension(from, TODAY, TODAY));
        assertEquals(ErReminderRules.Suspension.NONE, ErReminderRules.suspension(from, TODAY.minusDays(1), TODAY));
        assertEquals(ErReminderRules.Suspension.NONE, ErReminderRules.suspension(from, TODAY.minusDays(8), TODAY));
    }

    @Test
    void aSuspensionNotYetStartedOrNeverRecordedIsLeftAlone() {
        assertEquals(ErReminderRules.Suspension.NONE, ErReminderRules.suspension(null, null, TODAY));
        assertEquals(ErReminderRules.Suspension.NONE, ErReminderRules.suspension(TODAY.plusDays(3), TODAY.plusDays(5), TODAY));
        assertEquals(ErReminderRules.Suspension.NONE, ErReminderRules.suspension(TODAY.plusDays(3), null, TODAY));
    }

    @Test
    void anOpenEndedSuspensionIsQuestionedAtThirtyDaysThenWeekly() {
        assertEquals(ErReminderRules.Suspension.NONE, ErReminderRules.suspension(TODAY.minusDays(29), null, TODAY));
        assertEquals(ErReminderRules.Suspension.OPEN_LONG, ErReminderRules.suspension(TODAY.minusDays(30), null, TODAY));
        assertEquals(ErReminderRules.Suspension.NONE, ErReminderRules.suspension(TODAY.minusDays(31), null, TODAY));
        assertEquals(ErReminderRules.Suspension.OPEN_LONG, ErReminderRules.suspension(TODAY.minusDays(37), null, TODAY));
        assertEquals(ErReminderRules.Suspension.OPEN_LONG, ErReminderRules.suspension(TODAY.minusDays(44), null, TODAY));
    }

    @Test
    void theSuspensionWordsNameTheCaseAndTheDatesOnly() {
        String ends = ErReminderRules.suspensionMessage("ER-7", ErReminderRules.Suspension.ENDS_TOMORROW, TODAY.minusDays(5), TODAY.plusDays(1), TODAY);
        String open = ErReminderRules.suspensionMessage("ER-7", ErReminderRules.Suspension.OPEN_LONG, TODAY.minusDays(30), null, TODAY);
        assertTrue(ends.contains("2026-11-10") && ends.contains("extend it or bring the employee back"));
        assertTrue(open.contains("30 days") && open.contains("no end date"));
        assertTrue(ErReminderRules.suspensionTitle("ER-7", ErReminderRules.Suspension.OPEN_LONG).contains("no end date"));
    }

    @Test
    void aReviewPeriodIsRemindedTheDayBeforeAndWhenItEndsWithNoOutcome() {
        assertEquals(When.TOMORROW, ErReminderRules.review(null, TODAY.plusDays(1), TODAY));
        assertEquals(When.LATE, ErReminderRules.review(null, TODAY.minusDays(1), TODAY));
        assertEquals(When.NONE, ErReminderRules.review(null, TODAY.plusDays(5), TODAY));
        assertEquals(When.NONE, ErReminderRules.review(null, null, TODAY));
        assertEquals(When.NONE, ErReminderRules.review("EXTEND_REVIEW", TODAY.minusDays(1), TODAY));
        assertTrue(ErReminderRules.reviewMessage("ER-7", TODAY.plusDays(1), When.TOMORROW).contains("ends on 2026-11-10"));
        assertTrue(ErReminderRules.reviewMessage("ER-7", TODAY.minusDays(1), When.LATE).contains("no outcome has been recorded"));
    }
}
