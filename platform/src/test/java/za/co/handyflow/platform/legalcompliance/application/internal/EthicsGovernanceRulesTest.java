package za.co.handyflow.platform.legalcompliance.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class EthicsGovernanceRulesTest {

    private final LocalDate today = LocalDate.of(2026, 6, 1);

    @Test @DisplayName("Quorum: at or above the minimum; a minimum below 1 is never met")
    void quorum() {
        assertTrue(EthicsGovernanceRules.quorumMet(3, 3));
        assertFalse(EthicsGovernanceRules.quorumMet(2, 3));
        assertFalse(EthicsGovernanceRules.quorumMet(5, 0));
    }

    @Test @DisplayName("A committee is overdue to meet only once frequency has passed with nothing scheduled")
    void meetingOverdue() {
        LocalDate last = today.minusMonths(3);
        assertFalse(EthicsGovernanceRules.meetingOverdue(last, 3, false, today), "exactly on the date is not yet overdue");
        assertTrue(EthicsGovernanceRules.meetingOverdue(last.minusDays(1), 3, false, today));
        assertFalse(EthicsGovernanceRules.meetingOverdue(last.minusMonths(6), 3, true, today), "a scheduled meeting means not overdue");
        assertFalse(EthicsGovernanceRules.meetingOverdue(null, 3, false, today));
    }

    @Test @DisplayName("Charter review and action overdue are strict date comparisons")
    void overdue() {
        assertFalse(EthicsGovernanceRules.charterReviewOverdue(today.minusMonths(12), 12, today));
        assertTrue(EthicsGovernanceRules.charterReviewOverdue(today.minusMonths(12).minusDays(1), 12, today));
        assertFalse(EthicsGovernanceRules.actionOverdue(today, today));
        assertTrue(EthicsGovernanceRules.actionOverdue(today.minusDays(1), today));
        assertFalse(EthicsGovernanceRules.actionOverdue(null, today));
    }

    @Test @DisplayName("Gifts: strictly above the threshold needs approval")
    void gifts() {
        BigDecimal t = EthicsGovernanceRules.DEFAULT_GIFT_THRESHOLD;
        assertFalse(EthicsGovernanceRules.giftNeedsApproval(t, t));
        assertTrue(EthicsGovernanceRules.giftNeedsApproval(t.add(new BigDecimal("0.01")), t));
        assertFalse(EthicsGovernanceRules.giftNeedsApproval(null, t));
    }

    @Test @DisplayName("Weekly reminders fire on the anchor day and each 7 days after, never before")
    void weekly() {
        assertTrue(EthicsGovernanceRules.weeklyReminderDue(today, today));
        assertTrue(EthicsGovernanceRules.weeklyReminderDue(today.minusDays(14), today));
        assertFalse(EthicsGovernanceRules.weeklyReminderDue(today.minusDays(10), today));
        assertFalse(EthicsGovernanceRules.weeklyReminderDue(today.plusDays(7), today));
        assertFalse(EthicsGovernanceRules.weeklyReminderDue(null, today));
        assertEquals(today.plusMonths(12), EthicsGovernanceRules.nextReviewDue(today, 12));
        assertTrue(EthicsGovernanceRules.reviewOverdue(today.minusDays(1), today));
    }
}
