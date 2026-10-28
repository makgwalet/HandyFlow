package za.co.handyflow.platform.hr.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

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
}
