package za.co.handyflow.platform.hr.domain.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class WorkReminderRulesTest {

    private static final LocalDate ISSUED = LocalDate.of(2026, 10, 1);

    @Test
    void aLetterIsChasedFromADayAfterTheWeekIsUpThenWeekly() {
        assertFalse(WorkReminderRules.letterReminderToday("ISSUED", true, null, ISSUED, ISSUED.plusDays(7)));
        assertTrue(WorkReminderRules.letterReminderToday("ISSUED", true, null, ISSUED, ISSUED.plusDays(8)));
        assertFalse(WorkReminderRules.letterReminderToday("ISSUED", true, null, ISSUED, ISSUED.plusDays(9)));
        assertTrue(WorkReminderRules.letterReminderToday("ISSUED", true, null, ISSUED, ISSUED.plusDays(14)));
    }

    @Test
    void aLetterIsNotChasedWhenItNeedsNoAcknowledgementIsAcknowledgedOrIsNotIssued() {
        LocalDate day = ISSUED.plusDays(8);
        assertFalse(WorkReminderRules.letterReminderToday("ISSUED", false, null, ISSUED, day));
        assertFalse(WorkReminderRules.letterReminderToday("ISSUED", true, "yesterday", ISSUED, day));
        assertFalse(WorkReminderRules.letterReminderToday("VOID", true, null, ISSUED, day));
        assertFalse(WorkReminderRules.letterReminderToday("DRAFT", true, null, ISSUED, day));
        assertFalse(WorkReminderRules.letterReminderToday("ISSUED", true, null, null, day));
    }

    @Test
    void chasingStopsAfterEightWeeks() {
        assertTrue(WorkReminderRules.letterReminderToday("ISSUED", true, null, ISSUED, ISSUED.plusDays(7 + 56)));
        assertFalse(WorkReminderRules.letterReminderToday("ISSUED", true, null, ISSUED, ISSUED.plusDays(7 + 63)));
    }

    @Test
    void onlyARunningGoalPastItsDateIsLate() {
        LocalDate due = LocalDate.of(2026, 9, 30);
        assertTrue(WorkReminderRules.goalOverdueToday("ACTIVE", due, due.plusDays(1)));
        assertFalse(WorkReminderRules.goalOverdueToday("ACTIVE", due, due));
        assertTrue(WorkReminderRules.goalOverdueToday("ACTIVE", due, due.plusDays(7)));
        assertFalse(WorkReminderRules.goalOverdueToday("ACHIEVED", due, due.plusDays(1)));
        assertFalse(WorkReminderRules.goalOverdueToday("PROPOSED", due, due.plusDays(1)));
    }

    @Test
    void aProposalWaitsFiveDaysThenItsManagerIsReminded() {
        LocalDate on = LocalDate.of(2026, 10, 1);
        assertFalse(WorkReminderRules.proposalReminderToday("PROPOSED", on, on.plusDays(5)));
        assertTrue(WorkReminderRules.proposalReminderToday("PROPOSED", on, on.plusDays(6)));
        assertFalse(WorkReminderRules.proposalReminderToday("ACTIVE", on, on.plusDays(6)));
    }

    @Test
    void theWordsNeverSayWhatALetterIsAbout() {
        String m = WorkReminderRules.letterMessage("LTR-2026-0004", ISSUED);
        assertTrue(m.contains("LTR-2026-0004"));
        assertTrue(m.contains("2026-10-01"));
        assertTrue(WorkReminderRules.goalOverdueMessage("x".repeat(100), ISSUED, ISSUED).contains("\"" + "x".repeat(77) + "...\""));
    }
}
