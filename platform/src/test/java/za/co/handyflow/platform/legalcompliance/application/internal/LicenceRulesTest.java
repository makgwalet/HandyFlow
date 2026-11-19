package za.co.handyflow.platform.legalcompliance.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class LicenceRulesTest {

    private final LocalDate today = LocalDate.of(2026, 6, 1);

    @Test @DisplayName("Expired is derived: the expiry day itself is still valid, the day after is not; no expiry never expires")
    void expiry() {
        assertFalse(LicenceRules.expired(today, today));
        assertTrue(LicenceRules.expired(today.minusDays(1), today));
        assertFalse(LicenceRules.expired(null, today));
        assertNull(LicenceRules.daysToExpiry(null, today));
        assertEquals(Long.valueOf(-3), LicenceRules.daysToExpiry(today.minusDays(3), today));
    }

    @Test @DisplayName("Expiring soon covers today through the lead time, not yesterday or beyond")
    void expiringSoon() {
        assertTrue(LicenceRules.expiringSoon(today, 90, today));
        assertTrue(LicenceRules.expiringSoon(today.plusDays(90), 90, today));
        assertFalse(LicenceRules.expiringSoon(today.plusDays(91), 90, today));
        assertFalse(LicenceRules.expiringSoon(today.minusDays(1), 90, today));
    }

    @Test @DisplayName("Expiry reminders: lead day, then 30/14/7/3/1/0 only inside the lead time, then weekly once expired")
    void reminders() {
        assertTrue(LicenceRules.reminderDue(today.plusDays(90), 90, today));
        assertTrue(LicenceRules.reminderDue(today.plusDays(30), 90, today));
        assertFalse(LicenceRules.reminderDue(today.plusDays(31), 90, today));
        assertFalse(LicenceRules.reminderDue(today.plusDays(30), 14, today), "30 days is outside a 14 day lead time");
        assertTrue(LicenceRules.reminderDue(today.plusDays(14), 14, today));
        assertTrue(LicenceRules.reminderDue(today, 14, today));
        assertTrue(LicenceRules.reminderDue(today.minusDays(7), 14, today));
        assertFalse(LicenceRules.reminderDue(today.minusDays(6), 14, today));
        assertFalse(LicenceRules.reminderDue(null, 14, today));
    }

    @Test @DisplayName("Conditions: overdue the day after, due soon within 14 days, reminders at 14/7/1/0 then weekly")
    void conditions() {
        assertFalse(LicenceRules.conditionOverdue(today, today));
        assertTrue(LicenceRules.conditionOverdue(today.minusDays(1), today));
        assertFalse(LicenceRules.conditionOverdue(null, today));
        assertTrue(LicenceRules.conditionDueSoon(today.plusDays(14), today));
        assertFalse(LicenceRules.conditionDueSoon(today.plusDays(15), today));
        assertFalse(LicenceRules.conditionDueSoon(today.minusDays(1), today));
        for (int d : new int[]{14, 7, 1, 0}) assertTrue(LicenceRules.conditionReminderDue(today.plusDays(d), today), "d=" + d);
        assertFalse(LicenceRules.conditionReminderDue(today.plusDays(10), today));
        assertTrue(LicenceRules.conditionReminderDue(today.minusDays(14), today));
        assertFalse(LicenceRules.conditionReminderDue(today.minusDays(3), today));
    }

    @Test @DisplayName("Recurring condition: next due is the first date in the series after it was met, even when met very late")
    void nextDue() {
        LocalDate due = LocalDate.of(2026, 1, 31);
        assertEquals(LocalDate.of(2026, 4, 30), LicenceRules.nextConditionDue(due, 3, LocalDate.of(2026, 2, 10)));
        assertTrue(LicenceRules.nextConditionDue(due, 3, LocalDate.of(2026, 6, 1)).isAfter(LocalDate.of(2026, 6, 1)));
        assertEquals(LocalDate.of(2026, 7, 31), LicenceRules.nextConditionDue(due, 6, due));
    }
}
