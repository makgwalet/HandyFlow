package za.co.handyflow.platform.recruiter.domain.model;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Approval before a job is published and before an offer letter goes out. Each is optional per company. Whoever submits cannot approve
 * their own submission. No database or framework in here.
 */
public final class RecruiterApprovalRules {

    private RecruiterApprovalRules() {}

    public static final String NOT_SUBMITTED = "NOT_SUBMITTED", SUBMITTED = "SUBMITTED", APPROVED = "APPROVED", REJECTED = "REJECTED";

    // ── Jobs ──────────────────────────────────────────────────────────────────────────────────────

    /** Null when the job can be sent for approval. Only a draft that is not already waiting or approved. */
    public static String submitJobProblem(String jobStatus, String approvalStatus, String title, String description, BigDecimal salaryMin, BigDecimal salaryMax) {
        if (!"DRAFT".equals(jobStatus)) return "Only a draft job needs approval. This one is " + jobStatus.toLowerCase() + ".";
        if (SUBMITTED.equals(approvalStatus)) return "This job is already waiting for approval.";
        if (APPROVED.equals(approvalStatus)) return "This job is already approved.";
        if (title == null || title.isBlank()) return "The job needs a title.";
        if (description == null || description.isBlank()) return "The job needs a description.";
        if (salaryMin != null && salaryMax != null && salaryMin.compareTo(salaryMax) > 0) return "The lowest salary is above the highest.";
        return null;
    }

    /** Null when someone may decide. Not the person who submitted, and a rejection says why. */
    public static String decideProblem(String approvalStatus, UUID submittedBy, UUID decider, boolean approve, String note) {
        if (!SUBMITTED.equals(approvalStatus)) return "Nothing is waiting for approval.";
        if (decider == null) return "Your user could not be identified.";
        if (decider.equals(submittedBy)) return "You cannot decide on what you sent for approval yourself.";
        if (!approve && (note == null || note.isBlank())) return "Say why it is not approved.";
        if (note != null && note.length() > 500) return "The note is too long (500 characters at most).";
        return null;
    }

    /** Null when the job may go live. */
    public static String publishProblem(boolean approvalRequired, String approvalStatus) {
        if (!approvalRequired || APPROVED.equals(approvalStatus)) return null;
        if (SUBMITTED.equals(approvalStatus)) return "This job is waiting for approval and cannot be published yet.";
        if (REJECTED.equals(approvalStatus)) return "This job was not approved. Change it and send it for approval again.";
        return "This company needs a job to be approved before it is published. Send it for approval first.";
    }

    /** Changing a draft that was sent for approval, or approved, takes the approval away, so what is published is what was approved. */
    public static boolean editWithdrawsApproval(String jobStatus, String approvalStatus) {
        return "DRAFT".equals(jobStatus) && (SUBMITTED.equals(approvalStatus) || APPROVED.equals(approvalStatus));
    }

    // ── Offers ────────────────────────────────────────────────────────────────────────────────────

    /** Null when the offer can be sent for approval. */
    public static String submitOfferProblem(String stage, String approvalStatus, BigDecimal offeredSalary, boolean alreadySent) {
        if (!"OFFER".equals(stage)) return "Only an application at the offer stage has an offer to approve.";
        if (alreadySent) return "The offer letter was already sent.";
        if (offeredSalary == null || offeredSalary.signum() <= 0) return "Record the offered salary before sending the offer for approval.";
        if (SUBMITTED.equals(approvalStatus)) return "This offer is already waiting for approval.";
        if (APPROVED.equals(approvalStatus)) return "This offer is already approved.";
        return null;
    }

    /** Null when the offer letter may be sent. */
    public static String sendOfferProblem(boolean approvalRequired, String approvalStatus) {
        if (!approvalRequired || APPROVED.equals(approvalStatus)) return null;
        if (SUBMITTED.equals(approvalStatus)) return "This offer is waiting for approval and cannot be sent yet.";
        if (REJECTED.equals(approvalStatus)) return "This offer was not approved. Change the terms and send it for approval again.";
        return "This company needs an offer to be approved before the letter is sent. Send it for approval first.";
    }

    /** Null when the offer letter PDF may be downloaded. Same test as sending: an unapproved offer is not shown as a finished letter. */
    public static String offerPdfProblem(boolean approvalRequired, String approvalStatus) {
        if (!approvalRequired || APPROVED.equals(approvalStatus)) return null;
        if (SUBMITTED.equals(approvalStatus)) return "This offer is waiting for approval, so the letter cannot be downloaded yet.";
        if (REJECTED.equals(approvalStatus)) return "This offer was not approved. Change the terms and send it for approval again.";
        return "This company needs an offer to be approved before the letter is produced. Send it for approval first.";
    }

    public static String requestedTitle(boolean job) { return job ? "A job is waiting for your approval" : "An offer is waiting for your approval"; }

    /** Names the role only. Never the candidate, the salary or the submitter's note. */
    public static String requestedMessage(boolean job, String jobTitle, String submitter) {
        String who = submitter == null || submitter.isBlank() ? "A colleague" : submitter.trim();
        return who + (job ? " has asked you to approve the job \"" + clean(jobTitle) + "\" before it is published."
                : " has asked you to approve an offer for the \"" + clean(jobTitle) + "\" role before the letter is sent.");
    }

    public static String decidedTitle(boolean job, boolean approved) {
        return (job ? "Your job was " : "Your offer was ") + (approved ? "approved" : "not approved");
    }

    public static String decidedMessage(boolean job, boolean approved, String jobTitle, String decidedBy) {
        String who = decidedBy == null || decidedBy.isBlank() ? "An approver" : decidedBy.trim();
        String what = job ? "the job \"" + clean(jobTitle) + "\"" : "the offer for the \"" + clean(jobTitle) + "\" role";
        return who + (approved ? " approved " + what + ". You can " + (job ? "publish it now." : "send the letter now.")
                : " did not approve " + what + ". Open it to read why.");
    }

    private static String clean(String t) { return t == null || t.isBlank() ? "(untitled)" : t.trim(); }

    /** Whether the offered terms differ from the ones that were approved, in which case the approval no longer stands. */
    public static boolean offerChanged(BigDecimal oldSalary, String oldFrequency, java.time.LocalDate oldStart, String oldBenefits,
                                       BigDecimal newSalary, String newFrequency, java.time.LocalDate newStart, String newBenefits) {
        return !sameMoney(oldSalary, newSalary) || !java.util.Objects.equals(blank(oldFrequency), blank(newFrequency))
                || !java.util.Objects.equals(oldStart, newStart) || !java.util.Objects.equals(blank(oldBenefits), blank(newBenefits));
    }

    private static boolean sameMoney(BigDecimal a, BigDecimal b) {
        if (a == null || b == null) return a == b;
        return a.compareTo(b) == 0;
    }

    private static String blank(String s) { return s == null || s.isBlank() ? null : s.trim(); }
}
