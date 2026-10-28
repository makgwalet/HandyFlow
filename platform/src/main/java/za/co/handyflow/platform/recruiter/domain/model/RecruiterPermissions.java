package za.co.handyflow.platform.recruiter.domain.model;

import java.util.Set;

/** The permissions Recruiter endpoints may ask for, and the rule for the one check that depends on the request. */
public final class RecruiterPermissions {
    private RecruiterPermissions() {}

    public static final Set<String> ALL = Set.of(
            "RECRUITER_READ", "RECRUITER_MANAGE", "RECRUITER_ADMIN", "RECRUITER_APPROVE",
            "RECRUITER_AUDIT", "RECRUITER_CV", "RECRUITER_REPORTS", "RECRUITER_OFFER", "RECRUITER_HIRE");

    /** Moving a candidate to Hired is its own permission; every other stage stays with RECRUITER_MANAGE. Null means allowed. */
    public static String stageProblem(String targetStage, boolean mayHire) {
        if (targetStage != null && "HIRED".equalsIgnoreCase(targetStage.trim()) && !mayHire) {
            return "You do not have permission to mark a candidate as hired.";
        }
        return null;
    }
}
