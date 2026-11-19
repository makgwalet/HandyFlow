package za.co.handyflow.platform.legalcompliance.application.internal;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** Pure rules for committees and ethics. No framework dependencies. */
public final class EthicsGovernanceRules {

    private EthicsGovernanceRules() {}

    /** Default threshold (rand) above which a gift or hospitality entry needs approval. Stored on each entry when it is recorded. */
    public static final BigDecimal DEFAULT_GIFT_THRESHOLD = new BigDecimal("500.00");

    public static boolean quorumMet(int present, int quorumMin) { return quorumMin >= 1 && present >= quorumMin; }

    /**
     * A committee is overdue to meet when its last held meeting (or, if it has never met, the day it was set up) plus its meeting frequency has passed
     * and no meeting is scheduled for today or later.
     */
    public static boolean meetingOverdue(LocalDate lastHeldOrStart, int frequencyMonths, boolean upcomingScheduled, LocalDate today) {
        if (lastHeldOrStart == null || upcomingScheduled) return false;
        return today.isAfter(lastHeldOrStart.plusMonths(frequencyMonths));
    }

    public static boolean charterReviewOverdue(LocalDate lastReviewOrStart, int months, LocalDate today) {
        return lastReviewOrStart != null && today.isAfter(lastReviewOrStart.plusMonths(months));
    }

    public static boolean actionOverdue(LocalDate due, LocalDate today) { return due != null && today.isAfter(due); }

    /** Strictly above the threshold needs approval; exactly on it does not. */
    public static boolean giftNeedsApproval(BigDecimal value, BigDecimal threshold) { return value != null && threshold != null && value.compareTo(threshold) > 0; }

    public static LocalDate nextReviewDue(LocalDate from, int months) { return from.plusMonths(months); }

    public static boolean reviewOverdue(LocalDate nextDue, LocalDate today) { return nextDue != null && today.isAfter(nextDue); }

    /** True on the due date and then weekly while overdue; for daily sweeps that nag without repeating every day. */
    public static boolean weeklyReminderDue(LocalDate due, LocalDate today) {
        if (due == null) return false;
        long d = ChronoUnit.DAYS.between(due, today);
        return d >= 0 && d % 7 == 0;
    }
}
