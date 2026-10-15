package za.co.handyflow.platform.internalaudit.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.internalaudit.application.internal.ReminderRules.Stage;

import static org.junit.jupiter.api.Assertions.*;

class ReminderRulesTest {
    @Test
    void notLateMeansNothing() {
        assertEquals(Stage.NONE, ReminderRules.stageFor(0));
        assertEquals(Stage.NONE, ReminderRules.stageFor(-4));
    }

    @Test
    void firstDayThenWeeklyThenEscalatedFromFourteenDays() {
        assertEquals(Stage.REMIND, ReminderRules.stageFor(1));
        assertEquals(Stage.NONE, ReminderRules.stageFor(2));
        assertEquals(Stage.NONE, ReminderRules.stageFor(6));
        assertEquals(Stage.REMIND, ReminderRules.stageFor(7));
        assertEquals(Stage.NONE, ReminderRules.stageFor(13));
        assertEquals(Stage.ESCALATE, ReminderRules.stageFor(14));
        assertEquals(Stage.NONE, ReminderRules.stageFor(15));
        assertEquals(Stage.ESCALATE, ReminderRules.stageFor(21));
        assertEquals(Stage.ESCALATE, ReminderRules.stageFor(70));
    }

    @Test
    void reviewCommentsAreRemindedOnDayThreeThenWeekly() {
        assertFalse(ReminderRules.reviewReminderDue(2));
        assertTrue(ReminderRules.reviewReminderDue(3));
        assertFalse(ReminderRules.reviewReminderDue(4));
        assertFalse(ReminderRules.reviewReminderDue(6));
        assertTrue(ReminderRules.reviewReminderDue(7));
        assertTrue(ReminderRules.reviewReminderDue(14));
    }
}
