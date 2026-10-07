package za.co.handyflow.platform.clinic.application.internal;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecallRulesTest {

    private static final ZoneId SAST = RecallRules.CLINIC_ZONE;

    @Test
    void dueDateIsTheVisitDayInSouthAfricaPlusTheFollowUpDays() {
        assertEquals(LocalDate.of(2026, 9, 27), RecallRules.dueDate(Instant.parse("2026-09-20T08:00:00Z"), 7, SAST));
    }

    @Test
    void aVisitJustAfterMidnightInSouthAfricaCountsAsThatLocalDay() {
        // 00:30 on 21 September in Johannesburg is still 20 September in UTC
        assertEquals(LocalDate.of(2026, 9, 28), RecallRules.dueDate(Instant.parse("2026-09-20T22:30:00Z"), 7, SAST));
        assertEquals(LocalDate.of(2026, 9, 27), RecallRules.dueDate(Instant.parse("2026-09-20T22:30:00Z"), 7, ZoneId.of("UTC")));
    }

    @Test
    void dueOnTheDayAndAfterButNotBefore() {
        LocalDate due = LocalDate.of(2026, 9, 27);
        assertFalse(RecallRules.isDue(due, LocalDate.of(2026, 9, 26)));
        assertTrue(RecallRules.isDue(due, LocalDate.of(2026, 9, 27)));
        assertTrue(RecallRules.isDue(due, LocalDate.of(2026, 10, 1)));
    }

    @Test
    void overdueDaysAreWholeDaysPastAndNeverNegative() {
        LocalDate due = LocalDate.of(2026, 9, 27);
        assertEquals(0, RecallRules.overdueDays(due, LocalDate.of(2026, 9, 27)));
        assertEquals(0, RecallRules.overdueDays(due, LocalDate.of(2026, 9, 20)));
        assertEquals(4, RecallRules.overdueDays(due, LocalDate.of(2026, 10, 1)));
    }

    @Test
    void zeroDayFollowUpIsDueOnTheVisitDay() {
        Instant visit = Instant.parse("2026-09-20T08:00:00Z");
        assertTrue(RecallRules.isDue(RecallRules.dueDate(visit, 0, SAST), LocalDate.of(2026, 9, 20)));
    }
}
