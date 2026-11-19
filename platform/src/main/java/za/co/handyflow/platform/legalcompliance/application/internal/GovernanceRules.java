package za.co.handyflow.platform.legalcompliance.application.internal;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** Pure rules for policies, attestations and incidents. No Spring, no database. */
public final class GovernanceRules {

    /** Internal target, not a statutory deadline: a privacy incident should have its notification assessment within this many hours of discovery. */
    public static final long ASSESSMENT_TARGET_HOURS = 24;
    /** How far ahead the daily sweep warns that a policy review is coming up. */
    public static final int REVIEW_WARNING_DAYS = 30;

    private GovernanceRules() {}

    public static LocalDate nextReview(LocalDate publishedOn, int reviewMonths) { return publishedOn.plusMonths(reviewMonths); }

    public static boolean reviewOverdue(LocalDate nextReviewDue, boolean active, LocalDate today) { return active && nextReviewDue != null && nextReviewDue.isBefore(today); }

    public static boolean reviewDueSoon(LocalDate nextReviewDue, boolean active, LocalDate today) {
        return active && nextReviewDue != null && !nextReviewDue.isBefore(today) && ChronoUnit.DAYS.between(today, nextReviewDue) <= REVIEW_WARNING_DAYS;
    }

    /** Remind 3 days before the due date, on it, and then once a week while it is overdue. */
    public static boolean reminderDue(LocalDate due, LocalDate today) {
        long days = ChronoUnit.DAYS.between(today, due);
        if (days == 3 || days == 0) return true;
        return days < 0 && (-days) % 7 == 0;
    }

    public static int percent(long done, long total) { return total == 0 ? 0 : (int) Math.round(done * 100.0 / total); }

    public static boolean assessmentOverdue(Instant discoveredAt, boolean personalInfoInvolved, boolean assessed, Instant now) {
        return personalInfoInvolved && !assessed && Duration.between(discoveredAt, now).toHours() >= ASSESSMENT_TARGET_HOURS;
    }
}
