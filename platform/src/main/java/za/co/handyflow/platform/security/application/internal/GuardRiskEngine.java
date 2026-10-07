// security/application/internal/GuardRiskEngine.java
package za.co.handyflow.platform.security.application.internal;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns complaint and incident counts into recommendations for a person to review. Pure and configurable.
 * It recommends only. It never suspends, disciplines or dismisses anyone, and every message says a person decides.
 *
 * Complaints that were withdrawn or found unsubstantiated do not count towards the thresholds.
 */
public final class GuardRiskEngine {

    private GuardRiskEngine() {}

    public record Settings(int reviewAt, int warningAt, int investigationAt, int windowDays, int misconductAt, int misconductWindowDays,
                           boolean suspensionReviewOnCritical) {
        public static Settings defaults() { return new Settings(1, 3, 5, 90, 2, 365, true); }
    }

    /** Counts already restricted to the windows in Settings. */
    public record Facts(int complaintsInWindow, int substantiatedMisconductInWindow, int criticalIncidentsInWindow, int openUrgentComplaints) {}

    public record Recommendation(String code, String level, String title, String reason) {}

    /** Null when the settings make sense, otherwise what is wrong. */
    public static String validate(Settings s) {
        if (s.reviewAt() < 1 || s.warningAt() < 1 || s.investigationAt() < 1 || s.misconductAt() < 1) return "Thresholds must be at least 1";
        if (s.reviewAt() > s.warningAt() || s.warningAt() > s.investigationAt()) return "Thresholds must rise: supervisor review, then warning review, then formal investigation";
        if (s.windowDays() < 7 || s.windowDays() > 365) return "The complaint window must be between 7 and 365 days";
        if (s.misconductWindowDays() < 30 || s.misconductWindowDays() > 730) return "The misconduct window must be between 30 and 730 days";
        return null;
    }

    public static List<Recommendation> evaluate(Facts f, Settings s) {
        List<Recommendation> out = new ArrayList<>();
        int n = f.complaintsInWindow();
        String days = s.windowDays() + " days";
        if (n >= s.investigationAt())
            out.add(new Recommendation("FORMAL_INVESTIGATION", "URGENT", "Consider a formal investigation",
                    n + " complaints in the last " + days + " (threshold " + s.investigationAt() + ")."));
        else if (n >= s.warningAt())
            out.add(new Recommendation("WARNING_REVIEW", "WARN", "Review whether a warning is appropriate",
                    n + " complaints in the last " + days + " (threshold " + s.warningAt() + ")."));
        else if (n >= s.reviewAt())
            out.add(new Recommendation("SUPERVISOR_REVIEW", "INFO", "Supervisor review",
                    n + " complaint" + (n == 1 ? "" : "s") + " in the last " + days + " (threshold " + s.reviewAt() + ")."));

        if (f.substantiatedMisconductInWindow() >= s.misconductAt())
            out.add(new Recommendation("DISCIPLINARY_REVIEW", "URGENT", "Consider disciplinary action",
                    f.substantiatedMisconductInWindow() + " substantiated misconduct findings in the last " + s.misconductWindowDays() + " days (threshold " + s.misconductAt() + ")."));

        if (s.suspensionReviewOnCritical() && f.criticalIncidentsInWindow() > 0)
            out.add(new Recommendation("SUSPENSION_REVIEW", "URGENT", "Consider whether a suspension is needed while this is looked into",
                    f.criticalIncidentsInWindow() + " critical incident" + (f.criticalIncidentsInWindow() == 1 ? "" : "s") + " involving this guard in the last " + days + "."));

        if (f.openUrgentComplaints() > 0)
            out.add(new Recommendation("URGENT_COMPLAINT", "URGENT", "Urgent complaint still open",
                    f.openUrgentComplaints() + " open complaint" + (f.openUrgentComplaints() == 1 ? "" : "s") + " in a serious category or marked critical."));
        return out;
    }
}
