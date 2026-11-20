package za.co.handyflow.platform.legalcompliance.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.legalcompliance.application.internal.ReportService.Audience;
import za.co.handyflow.platform.legalcompliance.application.internal.ScheduleRules.Frequency;
import za.co.handyflow.platform.legalcompliance.domain.model.ReportSchedule;
import za.co.handyflow.platform.shared.TenantId;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ReportScheduleRulesTest {

    private static final LocalDate THU = LocalDate.of(2026, 11, 19);   // a Thursday

    @Test @DisplayName("Weekly: the next matching weekday strictly after the date")
    void weekly() {
        assertEquals(DayOfWeek.THURSDAY, THU.getDayOfWeek());
        assertEquals(LocalDate.of(2026, 11, 26), ScheduleRules.nextRun(Frequency.WEEKLY, 4, THU));          // same weekday: a week on, never today
        assertEquals(LocalDate.of(2026, 11, 20), ScheduleRules.nextRun(Frequency.WEEKLY, 5, THU));
        assertEquals(LocalDate.of(2026, 11, 23), ScheduleRules.nextRun(Frequency.WEEKLY, 1, THU));
    }

    @Test @DisplayName("Monthly: this month if the day is still ahead, otherwise next month, including across a year end")
    void monthly() {
        assertEquals(LocalDate.of(2026, 11, 25), ScheduleRules.nextRun(Frequency.MONTHLY, 25, THU));
        assertEquals(LocalDate.of(2026, 12, 1), ScheduleRules.nextRun(Frequency.MONTHLY, 1, THU));
        assertEquals(LocalDate.of(2026, 12, 19), ScheduleRules.nextRun(Frequency.MONTHLY, 19, THU));       // today's day is not "after" today
        assertEquals(LocalDate.of(2027, 1, 5), ScheduleRules.nextRun(Frequency.MONTHLY, 5, LocalDate.of(2026, 12, 20)));
        assertEquals(LocalDate.of(2026, 3, 28), ScheduleRules.nextRun(Frequency.MONTHLY, 28, LocalDate.of(2026, 2, 28)));
    }

    @Test @DisplayName("Quarterly: only January, April, July and October")
    void quarterly() {
        assertEquals(LocalDate.of(2027, 1, 5), ScheduleRules.nextRun(Frequency.QUARTERLY, 5, THU));
        assertEquals(LocalDate.of(2026, 4, 1), ScheduleRules.nextRun(Frequency.QUARTERLY, 1, LocalDate.of(2026, 2, 10)));
        assertEquals(LocalDate.of(2026, 7, 10), ScheduleRules.nextRun(Frequency.QUARTERLY, 10, LocalDate.of(2026, 4, 10)));
        assertEquals(LocalDate.of(2026, 10, 20), ScheduleRules.nextRun(Frequency.QUARTERLY, 20, LocalDate.of(2026, 10, 1)));
        assertEquals(LocalDate.of(2026, 7, 15), ScheduleRules.nextRun(Frequency.QUARTERLY, 15, LocalDate.of(2026, 5, 31)));
    }

    @Test @DisplayName("Days outside the allowed range are rejected")
    void days() {
        assertThrows(IllegalArgumentException.class, () -> ScheduleRules.validateDay(Frequency.WEEKLY, 0));
        assertThrows(IllegalArgumentException.class, () -> ScheduleRules.validateDay(Frequency.WEEKLY, 8));
        assertThrows(IllegalArgumentException.class, () -> ScheduleRules.validateDay(Frequency.MONTHLY, 29));
        assertThrows(IllegalArgumentException.class, () -> ScheduleRules.validateDay(Frequency.QUARTERLY, 0));
        assertThrows(IllegalArgumentException.class, () -> ScheduleRules.validateDay(null, 1));
        assertDoesNotThrow(() -> ScheduleRules.validateDay(Frequency.MONTHLY, 28));
        assertDoesNotThrow(() -> ScheduleRules.validateDay(Frequency.WEEKLY, 7));
    }

    @Test @DisplayName("Recipients: de-duplicated in order, at least one, at most 25")
    void recipients() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        assertEquals(List.of(a, b), ScheduleRules.recipients(java.util.Arrays.asList(a, b, a, null)));
        assertThrows(IllegalArgumentException.class, () -> ScheduleRules.recipients(List.of()));
        assertThrows(IllegalArgumentException.class, () -> ScheduleRules.recipients(null));
        List<UUID> many = new ArrayList<>();
        for (int i = 0; i < 26; i++) many.add(UUID.randomUUID());
        assertThrows(IllegalArgumentException.class, () -> ScheduleRules.recipients(many));
        assertEquals(25, ScheduleRules.recipients(many.subList(0, 25)).size());
    }

    @Test @DisplayName("Recipient lists survive being stored as text")
    void roundTrip() {
        List<UUID> ids = List.of(UUID.randomUUID(), UUID.randomUUID());
        assertEquals(ids, ScheduleRules.split(ScheduleRules.join(ids)));
        assertEquals(List.of(), ScheduleRules.split(null));
        assertEquals(List.of(), ScheduleRules.split(" "));
    }

    @Test @DisplayName("The notice carries the report name and a count, nothing from the rows")
    void message() {
        String m = ScheduleRules.message("Monthly risks", "Risk register", 42, false);
        assertTrue(m.contains("Risk register") && m.contains("42 row(s)") && m.contains("Monthly risks"));
        assertFalse(m.contains("cut"));
        assertTrue(ScheduleRules.message("x", "y", 5000, true).contains("cut at the row limit"));
    }

    private static ReportSchedule sample(Audience aud) {
        return ReportSchedule.create(TenantId.of(UUID.randomUUID()), "Weekly", "RISKS", Frequency.WEEKLY, 1, aud.name(), List.of(UUID.randomUUID()), THU, null, "me");
    }

    @Test @DisplayName("A new schedule is first due on its next date, and claiming moves it on")
    void dueAndClaim() {
        ReportSchedule s = sample(Audience.STAFF);
        assertEquals(LocalDate.of(2026, 11, 23), s.getNextRunOn());
        assertFalse(s.isDue(LocalDate.of(2026, 11, 22)));
        assertTrue(s.isDue(LocalDate.of(2026, 11, 23)));
        assertTrue(s.isDue(LocalDate.of(2026, 12, 20)), "a run missed while the system was down is still due");
        s.claim(LocalDate.of(2026, 12, 20));
        assertEquals(LocalDate.of(2026, 12, 21), s.getNextRunOn(), "one catch-up run, not one per missed week");
        assertFalse(s.isDue(LocalDate.of(2026, 12, 20)));
    }

    @Test @DisplayName("A failed run is retried tomorrow, and the creator is alerted once on the third failure")
    void failures() {
        ReportSchedule s = sample(Audience.STAFF);
        LocalDate d = LocalDate.of(2026, 11, 23);
        s.recordFailure(d, "boom");
        assertEquals(d.plusDays(1), s.getNextRunOn());
        assertFalse(s.justBecameAlertable());
        s.recordFailure(d.plusDays(1), "boom");
        s.recordFailure(d.plusDays(2), "boom");
        assertTrue(s.justBecameAlertable());
        s.recordFailure(d.plusDays(3), "boom");
        assertFalse(s.justBecameAlertable(), "said once, not every day");
        s.recordSuccess(d.plusDays(4), 12);
        assertEquals("OK", s.getLastStatus());
        assertEquals(0, s.getConsecutiveFailures());
        assertNull(s.getLastError());
        assertEquals(Integer.valueOf(12), s.getLastRowCount());
    }

    @Test @DisplayName("A long error is cut to fit its column")
    void longError() {
        ReportSchedule s = sample(Audience.STAFF);
        s.recordFailure(THU, "x".repeat(900));
        assertEquals(500, s.getLastError().length());
    }

    @Test @DisplayName("Resuming a paused schedule skips the dates missed while paused; pausing stops it being due")
    void pauseResume() {
        ReportSchedule s = sample(Audience.STAFF);
        s.setActive(false, THU);
        assertFalse(s.isDue(LocalDate.of(2027, 1, 1)));
        s.setActive(true, LocalDate.of(2027, 1, 1));
        assertEquals(LocalDate.of(2027, 1, 4), s.getNextRunOn());
    }

    @Test @DisplayName("Editing cannot leave a schedule with no name, no recipients or a bad day")
    void editValidation() {
        ReportSchedule s = sample(Audience.STAFF);
        assertThrows(IllegalArgumentException.class, () -> s.edit(" ", Frequency.WEEKLY, 1, List.of(UUID.randomUUID()), THU));
        assertThrows(IllegalArgumentException.class, () -> s.edit("n", Frequency.WEEKLY, 1, List.of(), THU));
        assertThrows(IllegalArgumentException.class, () -> s.edit("n", Frequency.MONTHLY, 31, List.of(UUID.randomUUID()), THU));
        assertEquals("Weekly", s.getName(), "a failed edit changes nothing");
    }

    @Test @DisplayName("Staff cannot reach an administrator's schedule; administrators reach both")
    void access() {
        assertTrue(ReportScheduleService.mayAccess(sample(Audience.STAFF), Audience.STAFF));
        assertFalse(ReportScheduleService.mayAccess(sample(Audience.ADMIN), Audience.STAFF));
        assertTrue(ReportScheduleService.mayAccess(sample(Audience.ADMIN), Audience.ADMIN));
        assertTrue(ReportScheduleService.mayAccess(sample(Audience.STAFF), Audience.ADMIN));
    }
}
