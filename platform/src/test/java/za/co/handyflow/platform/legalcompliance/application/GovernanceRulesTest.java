package za.co.handyflow.platform.legalcompliance.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.legalcompliance.application.internal.GovernanceRules;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

class GovernanceRulesTest {

    private final LocalDate today = LocalDate.of(2026, 11, 19);

    @Test @DisplayName("the next review is the publication date plus the review period")
    void nextReview() {
        assertEquals(LocalDate.of(2027, 11, 19), GovernanceRules.nextReview(today, 12));
        assertEquals(LocalDate.of(2027, 5, 19), GovernanceRules.nextReview(today, 6));
    }

    @Test @DisplayName("review overdue and due-soon apply only to active policies, with a 30 day window")
    void reviewWindow() {
        assertTrue(GovernanceRules.reviewOverdue(today.minusDays(1), true, today));
        assertFalse(GovernanceRules.reviewOverdue(today, true, today));
        assertFalse(GovernanceRules.reviewOverdue(today.minusDays(1), false, today), "a retired policy is never overdue");
        assertFalse(GovernanceRules.reviewOverdue(null, true, today));
        assertTrue(GovernanceRules.reviewDueSoon(today.plusDays(30), true, today));
        assertFalse(GovernanceRules.reviewDueSoon(today.plusDays(31), true, today));
        assertFalse(GovernanceRules.reviewDueSoon(today.minusDays(1), true, today), "overdue is not 'due soon'");
    }

    @Test @DisplayName("reminders go out 3 days before, on the day, and weekly once overdue")
    void reminders() {
        assertTrue(GovernanceRules.reminderDue(today.plusDays(3), today));
        assertTrue(GovernanceRules.reminderDue(today, today));
        assertFalse(GovernanceRules.reminderDue(today.plusDays(1), today));
        assertFalse(GovernanceRules.reminderDue(today.plusDays(10), today));
        assertTrue(GovernanceRules.reminderDue(today.minusDays(7), today));
        assertTrue(GovernanceRules.reminderDue(today.minusDays(14), today));
        assertFalse(GovernanceRules.reminderDue(today.minusDays(3), today));
    }

    @Test @DisplayName("completion percentage rounds and tolerates an empty campaign")
    void percent() {
        assertEquals(0, GovernanceRules.percent(0, 0));
        assertEquals(75, GovernanceRules.percent(3, 4));
        assertEquals(33, GovernanceRules.percent(1, 3));
        assertEquals(100, GovernanceRules.percent(5, 5));
    }

    @Test @DisplayName("the assessment target only applies to personal-information incidents that are not yet assessed")
    void assessmentTarget() {
        Instant now = Instant.parse("2026-11-19T12:00:00Z");
        assertTrue(GovernanceRules.assessmentOverdue(now.minus(25, ChronoUnit.HOURS), true, false, now));
        assertTrue(GovernanceRules.assessmentOverdue(now.minus(24, ChronoUnit.HOURS), true, false, now));
        assertFalse(GovernanceRules.assessmentOverdue(now.minus(23, ChronoUnit.HOURS), true, false, now));
        assertFalse(GovernanceRules.assessmentOverdue(now.minus(48, ChronoUnit.HOURS), true, true, now), "already assessed");
        assertFalse(GovernanceRules.assessmentOverdue(now.minus(48, ChronoUnit.HOURS), false, false, now), "no personal information");
    }
}
