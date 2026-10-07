// security/application/internal/GuardReviewRules.java
package za.co.handyflow.platform.security.application.internal;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** When a guard is due a supervisor review. Pure, so it can be tested directly. */
public final class GuardReviewRules {

    private GuardReviewRules() {}

    /** A guard should be reviewed at least this often. */
    public static final int INTERVAL_DAYS = 90;

    /** NONE: never reviewed. FOLLOW_UP_DUE: the latest review set a follow-up date that has arrived. OVERDUE: last review too long ago. Otherwise OK. */
    public static String dueState(LocalDate lastReviewOn, LocalDate latestFollowUp, LocalDate today) {
        if (lastReviewOn == null) return "NONE";
        if (latestFollowUp != null && !latestFollowUp.isAfter(today)) return "FOLLOW_UP_DUE";
        return ChronoUnit.DAYS.between(lastReviewOn, today) > INTERVAL_DAYS ? "OVERDUE" : "OK";
    }
}
