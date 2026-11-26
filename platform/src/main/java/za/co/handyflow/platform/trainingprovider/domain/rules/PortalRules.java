package za.co.handyflow.platform.trainingprovider.domain.rules;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Rules for the read-only learner and employer portals. The portals show what the provider has recorded. They never say a learner is competent,
 * accredited or compliant, they show attendance as counts only (no threshold is applied), and an employer sees only learners whose consent flag is set.
 */
public final class PortalRules {

    private PortalRules() {
    }

    public static final int MIN_REASON = 10;
    public static final String NOTE = "This shows what the training provider has recorded. Attendance is a count of sessions, not a result, and nothing here says anyone is competent.";
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    /** Why a learner cannot be invited, or null. */
    public static String inviteProblem(String email, String reason, String learnerStatus) {
        if (email == null || !EMAIL.matcher(email.trim()).matches()) {
            return "Enter a valid email address for the learner";
        }
        if (reason == null || reason.trim().length() < MIN_REASON) {
            return "Say why the learner is being given access, in at least " + MIN_REASON + " characters";
        }
        if (!"ACTIVE".equals(learnerStatus)) {
            return "Only an active learner can be given portal access";
        }
        return null;
    }

    /** Why access cannot be ended, or null. */
    public static String endProblem(String reason, String status) {
        if (!"PENDING".equals(status) && !"ACCEPTED".equals(status)) {
            return "This access has already ended";
        }
        if (reason == null || reason.trim().length() < MIN_REASON) {
            return "Say why the access is ending, in at least " + MIN_REASON + " characters";
        }
        return null;
    }

    /** An employer sees a learner only when the learner's consent flag is set and the record was not merged into another. */
    public static boolean employerMayView(boolean consentGiven, boolean merged) {
        return consentGiven && !merged;
    }

    public record Counts(int recorded, int present, int late, int absent, int excused) {
    }

    /** Counts of recorded sessions by status. No percentage and no threshold. */
    public static Counts count(List<String> statuses) {
        int p = 0, l = 0, a = 0, e = 0;
        for (String s : statuses) {
            if ("PRESENT".equals(s)) p++;
            else if ("LATE".equals(s)) l++;
            else if ("ABSENT".equals(s)) a++;
            else if ("EXCUSED".equals(s)) e++;
        }
        return new Counts(p + l + a + e, p, l, a, e);
    }

    /** The recorded completion outcome in plain words. This is a completion of the programme, not a competence decision. */
    public static String outcomeText(String outcome) {
        if ("COMPLETED".equals(outcome)) return "Recorded as completed";
        if ("DID_NOT_COMPLETE".equals(outcome)) return "Recorded as not completed";
        return "No outcome recorded yet";
    }
}
