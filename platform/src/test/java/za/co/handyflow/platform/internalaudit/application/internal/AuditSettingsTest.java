package za.co.handyflow.platform.internalaudit.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.internalaudit.application.internal.AuditSettings.*;

import static org.junit.jupiter.api.Assertions.*;

class AuditSettingsTest {
    @Test void standardBandsCoverEveryScoreOnceWithNoGaps() {
        Settings s = AuditSettings.current();
        assertFalse(s.custom());
        assertEquals(4, s.riskBands().size());
        assertEquals(5, s.riskBands().get(0).from());
        assertEquals(25, s.riskBands().get(3).to());
        for (int i = 1; i < 4; i++) assertEquals(s.riskBands().get(i - 1).to() + 1, s.riskBands().get(i).from());
    }

    @Test void customBandsAreShownAsEntered() {
        Settings s = AuditSettings.of(new Values(8, 13, 18, 10, 5, 2), true);
        assertTrue(s.custom());
        assertEquals(new Band("MEDIUM", 9, 13), s.riskBands().get(1));
        assertEquals(new Band("CRITICAL", 19, 25), s.riskBands().get(3));
        assertEquals(10, s.escalateAfterDays());
        assertEquals(AuditSettings.DEFAULTS, s.defaults());
    }

    @Test void standardValuesAreAccepted() {
        assertTrue(AuditSettings.problems(AuditSettings.DEFAULTS).isEmpty());
        assertTrue(AuditSettings.problems(new Values(5, 6, 24, 2, 1, 1)).isEmpty());
        assertTrue(AuditSettings.problems(new Values(10, 15, 20, 90, 30, 30)).isEmpty());
    }

    @Test void bandsMustRiseAndLeaveRoomForCritical() {
        assertEquals(1, AuditSettings.problems(new Values(4, 15, 20, 14, 7, 3)).size());
        assertEquals(1, AuditSettings.problems(new Values(10, 10, 20, 14, 7, 3)).size());
        assertEquals(1, AuditSettings.problems(new Values(10, 15, 15, 14, 7, 3)).size());
        assertEquals(1, AuditSettings.problems(new Values(10, 15, 25, 14, 7, 3)).size());
    }

    @Test void daysMustBeInRange() {
        assertEquals(1, AuditSettings.problems(new Values(10, 15, 20, 1, 7, 3)).size());
        assertEquals(1, AuditSettings.problems(new Values(10, 15, 20, 91, 7, 3)).size());
        assertEquals(1, AuditSettings.problems(new Values(10, 15, 20, 14, 0, 3)).size());
        assertEquals(1, AuditSettings.problems(new Values(10, 15, 20, 14, 31, 3)).size());
        assertEquals(1, AuditSettings.problems(new Values(10, 15, 20, 14, 7, 0)).size());
    }

    @Test void everythingWrongIsReportedAtOnce() {
        assertEquals(6, AuditSettings.problems(new Values(0, 0, 30, 0, 0, 0)).size());
    }

    @Test void standardReminderTimingIsTheTimingTheRulesUse() {
        Values d = AuditSettings.DEFAULTS;
        assertEquals(ReminderRules.Stage.ESCALATE, ReminderRules.stageFor(d.escalateAfterDays()));
        assertEquals(ReminderRules.Stage.REMIND, ReminderRules.stageFor(d.repeatEveryDays()));
        assertTrue(ReminderRules.reviewReminderDue(d.firstReviewReminderDay()));
        assertFalse(ReminderRules.reviewReminderDue(d.firstReviewReminderDay() + 1));
    }

    @Test void customReminderTimingIsFollowed() {
        assertEquals(ReminderRules.Stage.REMIND, ReminderRules.stageFor(3, 14, 3));
        assertEquals(ReminderRules.Stage.NONE, ReminderRules.stageFor(7, 14, 3));
        assertEquals(ReminderRules.Stage.ESCALATE, ReminderRules.stageFor(15, 14, 3));
        assertEquals(ReminderRules.Stage.REMIND, ReminderRules.stageFor(12, 14, 3));
        assertTrue(ReminderRules.reviewReminderDue(2, 2, 5));
        assertFalse(ReminderRules.reviewReminderDue(3, 2, 5));
        assertTrue(ReminderRules.reviewReminderDue(5, 2, 5));
        assertTrue(ReminderRules.reviewReminderDue(10, 2, 5));
    }

    @Test void rolesGetReadableLabels() {
        assertEquals("Head Of Internal Audit", AuditSettings.current().engagementRoles().get(0).label());
        assertEquals(5, AuditSettings.current().engagementRoles().size());
    }
}
