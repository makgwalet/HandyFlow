package za.co.handyflow.platform.recruiter.domain.model;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * What staff may do to an application, in the order a hire happens. Pure; the service asks these before it changes anything, so the same
 * answer holds whichever screen or API call makes the request.
 * <p>
 * An application can sit in any live stage and be moved between them, but it cannot be hired without an offer, an offer needs terms, and a company
 * that requires offer approval cannot hire on an offer nobody approved. Withdrawal belongs to the candidate and is not a staff move.
 */
public final class RecruiterWorkflowRules {
    private RecruiterWorkflowRules() {}

    public static final List<String> STAGES = List.of("APPLIED", "SCREENING", "INTERVIEW", "ASSESSMENT", "OFFER", "HIRED");
    public static final int MAX_REASON = 500;
    private static final Duration PAST_GRACE = Duration.ofMinutes(10);

    /** Why the application cannot be moved from {@code from} to {@code to}, or null when it can. */
    public static String stageMoveProblem(String from, String to, String rejectionReason, BigDecimal offeredSalary,
                                          boolean offerApprovalRequired, String offerApprovalStatus) {
        String target = to == null ? "" : to.trim().toUpperCase();
        boolean reject = "REJECTED".equals(target);
        if (!reject && !STAGES.contains(target)) return "Choose a valid stage.";
        if (target.equals(from)) return "The application is already in that stage.";
        if (reject) {
            if (rejectionReason == null || rejectionReason.isBlank()) return "Give the reason for rejecting this application.";
            if (rejectionReason.trim().length() > MAX_REASON) return "The reason is too long (" + MAX_REASON + " characters at most).";
            return null;
        }
        if ("OFFER".equals(target) && (offeredSalary == null || offeredSalary.signum() <= 0))
            return "Enter the offered salary before moving the application to Offer.";
        if ("HIRED".equals(target)) {
            if (!"OFFER".equals(from)) return "Move the application to Offer before marking it hired.";
            if (offerApprovalRequired && !RecruiterApprovalRules.APPROVED.equals(offerApprovalStatus))
                return "This company needs the offer approved before the candidate can be marked hired.";
        }
        return null;
    }

    /** Why an interview cannot be booked now, or null when it can. A time is optional; one in the past is not. */
    public static String scheduleProblem(Instant scheduledAt, Instant now, boolean samePendingInterviewExists) {
        if (scheduledAt != null && scheduledAt.isBefore(now.minus(PAST_GRACE))) return "The interview time is in the past.";
        if (scheduledAt != null && samePendingInterviewExists) return "An interview for this application is already booked for that time.";
        return null;
    }

    /** Why the offer letter cannot be sent for an application in this stage, or null when it can. */
    public static String offerSendProblem(String stage) {
        return "OFFER".equals(stage) ? null : "An offer letter can only be sent while the application is at the Offer stage.";
    }
}
