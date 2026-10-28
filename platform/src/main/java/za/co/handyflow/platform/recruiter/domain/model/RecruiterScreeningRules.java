package za.co.handyflow.platform.recruiter.domain.model;

import java.math.BigDecimal;
import java.util.Set;

/**
 * Screening questions a job asks and how answers are checked. Pure. A knockout only marks an answer as not meeting the criteria so a person can look
 * at it; it never rejects anyone.
 */
public final class RecruiterScreeningRules {
    private RecruiterScreeningRules() {}

    public static final Set<String> TYPES = Set.of("YES_NO", "NUMBER", "TEXT");
    public static final int MAX_QUESTIONS = 15, MIN_TEXT = 3, MAX_TEXT = 300, MAX_ANSWER = 1000;
    private static final BigDecimal LIMIT = new BigDecimal("1000000000");

    /** Why this question cannot be saved, or null. */
    public static String questionProblem(String text, String type, boolean required, String expected, BigDecimal minimum) {
        if (text == null || text.trim().length() < MIN_TEXT) return "Write the question (at least " + MIN_TEXT + " characters).";
        if (text.trim().length() > MAX_TEXT) return "A question can be " + MAX_TEXT + " characters at most.";
        if (type == null || !TYPES.contains(type)) return "Choose Yes/No, Number or Text for each question.";
        boolean hasExpected = expected != null && !expected.isBlank();
        if (hasExpected && !"YES_NO".equals(type)) return "Only a Yes/No question can have a required answer.";
        if (hasExpected && !Set.of("YES", "NO").contains(expected.trim().toUpperCase())) return "The required answer must be Yes or No.";
        if (minimum != null && !"NUMBER".equals(type)) return "Only a Number question can have a minimum.";
        if (minimum != null && (minimum.signum() < 0 || minimum.compareTo(LIMIT) > 0)) return "The minimum is out of range.";
        if ((hasExpected || minimum != null) && !required) return "A question with a knockout criterion must be required.";
        return null;
    }

    public static String listProblem(int count) {
        return count > MAX_QUESTIONS ? "A job can have " + MAX_QUESTIONS + " screening questions at most." : null;
    }

    /** Why this answer cannot be accepted, or null. */
    public static String answerProblem(String question, String type, boolean required, String answer) {
        boolean blank = answer == null || answer.isBlank();
        if (blank) return required ? "Please answer: " + question : null;
        if (answer.trim().length() > MAX_ANSWER) return "Your answer to \"" + question + "\" is too long (" + MAX_ANSWER + " characters at most).";
        if ("YES_NO".equals(type) && !Set.of("YES", "NO").contains(answer.trim().toUpperCase())) return "Answer Yes or No: " + question;
        if ("NUMBER".equals(type)) {
            try {
                BigDecimal n = new BigDecimal(answer.trim());
                if (n.abs().compareTo(LIMIT) > 0) return "That number is out of range: " + question;
            } catch (NumberFormatException e) { return "Enter a number: " + question; }
        }
        return null;
    }

    public static boolean isKnockout(String expected, BigDecimal minimum) {
        return (expected != null && !expected.isBlank()) || minimum != null;
    }

    /** Whether the answer meets the criteria: null when there is no criterion or no answer. Assumes the answer passed {@link #answerProblem}. */
    public static Boolean meets(String type, String expected, BigDecimal minimum, String answer) {
        if (answer == null || answer.isBlank()) return null;
        if ("YES_NO".equals(type) && expected != null && !expected.isBlank()) return expected.trim().equalsIgnoreCase(answer.trim());
        if ("NUMBER".equals(type) && minimum != null) return new BigDecimal(answer.trim()).compareTo(minimum) >= 0;
        return null;
    }

    /** A required knockout question left unanswered is treated as not meeting the criteria, but only answered ones reach {@link #meets}. */
    public static String clean(String answer) { return answer == null || answer.isBlank() ? null : answer.trim(); }
}
