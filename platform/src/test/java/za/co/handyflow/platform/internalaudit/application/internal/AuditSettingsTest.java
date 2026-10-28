package za.co.handyflow.platform.internalaudit.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.internalaudit.application.internal.AuditSettings.*;

import static org.junit.jupiter.api.Assertions.*;

class AuditSettingsTest {
    @Test void bandsCoverEveryScoreOnceWithNoGaps() {
        Settings s = AuditSettings.current();
        assertEquals(4, s.riskBands().size());
        assertEquals(s.scoreMin(), s.riskBands().get(0).from());
        assertEquals(s.scoreMax(), s.riskBands().get(3).to());
        for (int i = 1; i < 4; i++) assertEquals(s.riskBands().get(i - 1).to() + 1, s.riskBands().get(i).from());
    }

    @Test void reminderTimingIsTheTimingTheRulesUse() {
        Settings s = AuditSettings.current();
        assertEquals(ReminderRules.Stage.ESCALATE, ReminderRules.stageFor(s.escalateAfterDays()));
        assertEquals(ReminderRules.Stage.REMIND, ReminderRules.stageFor(s.repeatEveryDays()));
        assertTrue(ReminderRules.reviewReminderDue(s.firstReviewReminderDay()));
        assertFalse(ReminderRules.reviewReminderDue(s.firstReviewReminderDay() + 1));
    }

    @Test void rolesGetReadableLabels() {
        assertEquals("Head Of Internal Audit", AuditSettings.current().engagementRoles().get(0).label());
        assertEquals(5, AuditSettings.current().engagementRoles().size());
    }
}
