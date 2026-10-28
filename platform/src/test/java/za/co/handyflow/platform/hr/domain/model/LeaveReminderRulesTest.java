package za.co.handyflow.platform.hr.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LeaveReminderRulesTest {

    @Test
    void remindsOnTheFirstOfOctoberNovemberDecemberAndOnThe15thOfDecember() {
        for (String d : new String[]{"2026-10-01", "2026-11-01", "2026-12-01", "2026-12-15"})
            assertTrue(LeaveReminderRules.reminderToday(LocalDate.parse(d)), d);
        for (String d : new String[]{"2026-09-01", "2026-10-02", "2026-10-15", "2026-11-15", "2026-12-16", "2026-12-31", "2027-01-01", "2026-01-01"})
            assertFalse(LeaveReminderRules.reminderToday(LocalDate.parse(d)), d);
    }

    @Test
    void daysAreWrittenWithoutTrailingZerosAndWithTheRightPlural() {
        assertEquals("1 day", LeaveReminderRules.days(new BigDecimal("1.00")));
        assertEquals("2.5 days", LeaveReminderRules.days(new BigDecimal("2.50")));
        assertEquals("10 days", LeaveReminderRules.days(new BigDecimal("10.00")));
        assertEquals("0.5 days", LeaveReminderRules.days(new BigDecimal("0.5")));
    }

    @Test
    void theEmployeeIsToldHowManyDaysTheLimitAndTheYear() {
        String title = LeaveReminderRules.employeeTitle(new BigDecimal("4.50"));
        String msg = LeaveReminderRules.employeeMessage(new BigDecimal("4.50"), new BigDecimal("10"), new BigDecimal("12.00"), 2026);
        assertEquals("You may lose 4.5 days of annual leave at year end", title);
        assertTrue(msg.contains("at most 10 days"));
        assertTrue(msg.contains("into 2027"));
        assertTrue(msg.contains("You have 12 days now"));
        assertTrue(msg.contains("31 December 2026"));
        assertTrue(msg.contains("lose 4.5 days"));
    }

    @Test
    void theAdministratorsSeeACountAndNoNames() {
        assertEquals("1 employee could lose annual leave at year end", LeaveReminderRules.adminTitle(1));
        assertEquals("3 employees could lose annual leave at year end", LeaveReminderRules.adminTitle(3));
        String m = LeaveReminderRules.adminMessage(3, new BigDecimal("10"), 2026);
        assertTrue(m.startsWith("3 employees have more annual leave than the 10 days"));
        assertTrue(LeaveReminderRules.adminMessage(1, new BigDecimal("10"), 2026).startsWith("1 employee has"));
    }

    // ── Carried leave that expires ───────────────────────────────────────────

    private static final LocalDate EXPIRY = LocalDate.of(2027, 6, 30);

    @Test
    void expiryIsRemindedThirtyFourteenSevenAndOneDayBefore() {
        for (int before : new int[]{30, 14, 7, 1}) assertTrue(LeaveReminderRules.expiryReminderToday(EXPIRY, EXPIRY.minusDays(before)), "d-" + before);
        for (int before : new int[]{31, 29, 15, 13, 8, 6, 2, 0}) assertFalse(LeaveReminderRules.expiryReminderToday(EXPIRY, EXPIRY.minusDays(before)), "d-" + before);
        assertFalse(LeaveReminderRules.expiryReminderToday(EXPIRY, EXPIRY.plusDays(1)));
        assertFalse(LeaveReminderRules.expiryReminderToday(null, EXPIRY));
    }

    @Test
    void theEmployeeIsToldHowManyDaysAndWhen() {
        String m = LeaveReminderRules.expiryEmployeeMessage(new BigDecimal("6"), EXPIRY, EXPIRY.minusDays(14), 2027);
        assertTrue(m.contains("6 days") && m.contains("after 2027-06-30") && m.contains("14 days from now"));
        assertTrue(LeaveReminderRules.expiryEmployeeMessage(new BigDecimal("1"), EXPIRY, EXPIRY.minusDays(1), 2027).contains("1 day from now"));
        assertEquals("6 days of your carried-over leave will expire", LeaveReminderRules.expiryEmployeeTitle(new BigDecimal("6")));
    }

    @Test
    void theManagerGetsOneMessageListingTheTeam() {
        List<LeaveReminderRules.Line> lines = List.of(new LeaveReminderRules.Line("Thandi Nkosi", new BigDecimal("6")), new LeaveReminderRules.Line("Sipho Dube", new BigDecimal("1")));
        assertEquals("Thandi Nkosi (6 days), Sipho Dube (1 day)", LeaveReminderRules.teamList(lines));
        assertEquals("2 people in your team will lose carried-over leave", LeaveReminderRules.teamTitle(2, true));
        assertEquals("1 person in your team could lose annual leave at year end", LeaveReminderRules.teamTitle(1, false));
        assertTrue(LeaveReminderRules.teamExpiryMessage(lines, EXPIRY).contains("2027-06-30"));
        assertTrue(LeaveReminderRules.teamCapMessage(lines, new BigDecimal("10"), 2026).contains("at most 10 days of annual leave into 2027"));
    }

    @Test
    void aLongTeamListIsCut() {
        List<LeaveReminderRules.Line> lines = new java.util.ArrayList<>();
        for (int i = 1; i <= 13; i++) lines.add(new LeaveReminderRules.Line("P" + i, new BigDecimal("2")));
        String s = LeaveReminderRules.teamList(lines);
        assertTrue(s.contains("P10 (2 days)") && !s.contains("P11") && s.endsWith("and 3 more"));
    }
}
