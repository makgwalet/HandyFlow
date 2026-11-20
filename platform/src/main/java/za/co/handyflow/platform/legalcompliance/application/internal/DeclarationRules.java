package za.co.handyflow.platform.legalcompliance.application.internal;

import za.co.handyflow.platform.legalcompliance.domain.model.DeclarationStatus;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** Pure rules for declaration campaigns. */
public final class DeclarationRules {

    private DeclarationRules() {}

    /**
     * People who have not answered are reminded 7 days before the due date, the day before, on the day, and then once a week while overdue.
     * Anything else (including a campaign more than a week from its due date) sends nothing, so the same sweep can run every day.
     */
    public static boolean remindToday(LocalDate due, LocalDate today) {
        long daysLeft = ChronoUnit.DAYS.between(today, due);
        if (daysLeft == 7 || daysLeft == 1 || daysLeft == 0) return true;
        return daysLeft < 0 && (-daysLeft) % 7 == 0;
    }

    public static String reminderText(LocalDate due, LocalDate today) {
        long daysLeft = ChronoUnit.DAYS.between(today, due);
        if (daysLeft > 1) return "due in " + daysLeft + " days (" + due + ")";
        if (daysLeft == 1) return "due tomorrow (" + due + ")";
        if (daysLeft == 0) return "due today";
        return "overdue by " + (-daysLeft) + " day" + (daysLeft == -1 ? "" : "s") + " (was due " + due + ")";
    }

    /** An earlier declaration that "unchanged" can vouch for: still live, not withdrawn and not refused. */
    public static boolean confirmable(DeclarationStatus s) {
        return s == DeclarationStatus.DECLARED || s == DeclarationStatus.UNDER_REVIEW || s == DeclarationStatus.CLEARED || s == DeclarationStatus.MITIGATED;
    }

    /** Only a declaration a reviewer has decided as cleared or mitigated has a review date for "unchanged" to extend. One still awaiting review has none. */
    public static boolean extendsReview(DeclarationStatus s) { return s == DeclarationStatus.CLEARED || s == DeclarationStatus.MITIGATED; }

    /** True when the review date is already what confirming today would set, so a second "unchanged" the same day changes nothing and adds no second note. */
    public static boolean alreadyConfirmed(LocalDate nextReviewDue, int reviewMonths, LocalDate today) { return nextReviewDue != null && nextReviewDue.equals(today.plusMonths(reviewMonths)); }

    /** Whole-number percentage; 0 when there is nobody in the campaign. */
    public static int percent(long answered, long total) { return total == 0 ? 0 : (int) Math.round(answered * 100.0 / total); }
}
