package za.co.handyflow.platform.recruiter.domain.model;

import java.util.List;
import java.util.Set;

/** Checks a candidate must pass before they are hired, and what each outcome allows. Pure. */
public final class RecruiterVerificationRules {
    private RecruiterVerificationRules() {}

    public static final Set<String> TYPES = Set.of("IDENTITY", "RIGHT_TO_WORK", "QUALIFICATION", "REFERENCE", "BACKGROUND", "OTHER");
    public static final String PENDING = "PENDING", VERIFIED = "VERIFIED", FAILED = "FAILED", WAIVED = "WAIVED";
    public static final int MIN_DESCRIPTION = 3, MAX_DESCRIPTION = 200, MAX_NOTE = 500;

    /** One check, as far as the hiring decision is concerned. */
    public record Check(boolean required, String status) {}

    /** Why a check cannot be added to an application in this stage, or null. */
    public static String addProblem(String type, String description, String stage) {
        if (type == null || !TYPES.contains(type)) return "Choose the kind of check.";
        if (description == null || description.trim().length() < MIN_DESCRIPTION) return "Say what is being checked (at least " + MIN_DESCRIPTION + " characters).";
        if (description.trim().length() > MAX_DESCRIPTION) return "The description is too long (" + MAX_DESCRIPTION + " characters at most).";
        if ("HIRED".equals(stage) || "REJECTED".equals(stage) || "WITHDRAWN".equals(stage)) return "This application is closed.";
        return null;
    }

    /** Why this decision cannot be recorded, or null. A pending or failed check can be decided; a verified or waived one is final. */
    public static String decisionProblem(String current, String decision, String note) {
        String d = decision == null ? "" : decision.trim().toUpperCase();
        if (!Set.of(VERIFIED, FAILED, WAIVED).contains(d)) return "Choose verified, failed or waived.";
        if (VERIFIED.equals(current) || WAIVED.equals(current)) return "This check has already been " + current.toLowerCase() + ".";
        if ((FAILED.equals(d) || WAIVED.equals(d)) && (note == null || note.isBlank())) return "Give the reason.";
        if (note != null && note.trim().length() > MAX_NOTE) return "The note is too long (" + MAX_NOTE + " characters at most).";
        return null;
    }

    /** Waiving a required check is a decision to hire without it, so it needs the approver permission. */
    public static boolean needsApprover(String decision) { return WAIVED.equals(decision == null ? "" : decision.trim().toUpperCase()); }

    /** Why the candidate cannot be marked hired because of their checks, or null. An application with no checks is not held up. */
    public static String hireProblem(List<Check> checks) {
        int outstanding = 0;
        for (Check c : checks) {
            if (!c.required()) continue;
            if (FAILED.equals(c.status())) return "A required check failed. Verify it again or waive it with a reason before hiring.";
            if (PENDING.equals(c.status())) outstanding++;
        }
        return outstanding == 0 ? null : outstanding + (outstanding == 1 ? " required check is" : " required checks are") + " still outstanding.";
    }

    public static String removeProblem(String status) {
        return PENDING.equals(status) ? null : "Only a check that has not been decided can be removed.";
    }
}
