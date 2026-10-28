package za.co.handyflow.platform.recruiter.domain.model;

import java.util.Set;

/**
 * Checks on what staff and candidates may send to the recruiter module. Pure.
 * A CV must be a real PDF of a sensible size with a plain file name; an interview outcome must be one of the known results, recorded
 * once, on an interview that is still waiting for its result, for an application that is still in play.
 */
public final class RecruiterSecurityRules {
    private RecruiterSecurityRules() {}

    public static final int MAX_CV_BYTES = 5 * 1024 * 1024;
    public static final int MAX_FILE_NAME = 200;
    public static final int MAX_NOTES = 2000;
    public static final Set<String> OUTCOMES = Set.of("PASSED", "FAILED", "NO_SHOW", "CANCELLED");

    /** Why this CV cannot be kept, or null when it can. A missing CV is fine; an empty or unreadable one is not. */
    public static String cvProblem(byte[] bytes, String fileName) {
        if (bytes == null || bytes.length == 0) return "The uploaded CV is empty.";
        if (bytes.length > MAX_CV_BYTES) return "The CV is too large (" + (MAX_CV_BYTES / (1024 * 1024)) + " MB at most).";
        if (!isPdf(bytes)) return "The CV must be a PDF file.";
        if (fileName != null && !fileName.isBlank()) {
            String n = fileName.trim();
            if (n.length() > MAX_FILE_NAME) return "The CV file name is too long (" + MAX_FILE_NAME + " characters at most).";
            if (n.contains("/") || n.contains("\\") || n.contains("..") || n.chars().anyMatch(c -> c < 32 || c == 127))
                return "The CV file name contains characters that are not allowed.";
            if (!n.toLowerCase().endsWith(".pdf")) return "The CV file name must end in .pdf.";
        }
        return null;
    }

    private static boolean isPdf(byte[] b) {
        // "%PDF-" at the very start of the file.
        return b.length >= 5 && b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F' && b[4] == '-';
    }

    /** Why this interview result cannot be recorded, or null when it can. */
    public static String outcomeProblem(String outcome, Integer score, String notes) {
        if (outcome == null || !OUTCOMES.contains(outcome.trim().toUpperCase()))
            return "Choose the result: passed, failed, no show or cancelled.";
        if (score != null && (score < 0 || score > 100)) return "The score must be between 0 and 100.";
        if (notes != null && notes.length() > MAX_NOTES) return "The notes are too long (" + MAX_NOTES + " characters at most).";
        return null;
    }

    /** Why a result cannot be recorded for this interview and application now, or null when it can. */
    public static String outcomeStateProblem(String interviewOutcome, boolean applicationActive) {
        if (!applicationActive) return "This application is no longer active, so an interview result cannot be recorded.";
        if (!"PENDING".equals(interviewOutcome)) return "This interview already has a result or was rescheduled.";
        return null;
    }
}
