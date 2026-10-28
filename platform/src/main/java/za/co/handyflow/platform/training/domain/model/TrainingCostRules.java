package za.co.handyflow.platform.training.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/** What a training session cost, and the pass mark of a course. Pure. */
public final class TrainingCostRules {
    private TrainingCostRules() {}

    public static final BigDecimal MAX_AMOUNT = new BigDecimal("10000000");
    public static final int MAX_NOTE = 200;
    public static final Map<String, String> CATEGORIES = new LinkedHashMap<>();
    static {
        CATEGORIES.put("TRAINER_FEE", "Trainer fee"); CATEGORIES.put("VENUE", "Venue"); CATEGORIES.put("MATERIALS", "Materials");
        CATEGORIES.put("TRAVEL", "Travel"); CATEGORIES.put("CERTIFICATION_FEE", "Certification or exam fee"); CATEGORIES.put("OTHER", "Other");
    }

    /** Why this cost cannot be recorded, or null. */
    public static String costProblem(String category, BigDecimal amount, LocalDate incurredOn, String note, LocalDate today) {
        if (category == null || !CATEGORIES.containsKey(category)) return "Choose what the cost was for.";
        if (amount == null || amount.signum() <= 0) return "Enter the amount, more than zero.";
        if (amount.stripTrailingZeros().scale() > 2) return "An amount has at most two decimal places.";
        if (amount.compareTo(MAX_AMOUNT) > 0) return "That amount is too large. Check it.";
        if (incurredOn == null) return "Enter the date the cost was incurred.";
        if (incurredOn.isAfter(today)) return "The date cannot be in the future.";
        if (incurredOn.isBefore(today.minusYears(5))) return "The date is too far back.";
        if (note != null && note.trim().length() > MAX_NOTE) return "The note is too long (" + MAX_NOTE + " characters at most).";
        return null;
    }

    /** Total divided by the people who took part, to the cent; null when nobody did. */
    public static BigDecimal costPerPerson(BigDecimal total, long people) {
        if (total == null || people <= 0) return null;
        return total.divide(BigDecimal.valueOf(people), 2, RoundingMode.HALF_UP);
    }

    /** Why this pass mark cannot be kept, or null. Empty means the course has none. */
    public static String passMarkProblem(BigDecimal mark) {
        if (mark == null) return null;
        if (mark.signum() < 0 || mark.compareTo(new BigDecimal("100")) > 0) return "The pass mark must be between 0 and 100.";
        if (mark.stripTrailingZeros().scale() > 2) return "A pass mark has at most two decimal places.";
        return null;
    }

    /** The result of a score against a pass mark. {@code problem} is set when the result cannot be decided. */
    public record Decision(boolean passed, String problem) {}

    /** With a pass mark the score decides; without one the person recording the result decides. */
    public static Decision decide(BigDecimal score, BigDecimal passMark, boolean requestedPassed) {
        if (passMark == null) return new Decision(requestedPassed, null);
        if (score == null) return new Decision(false, "This course has a pass mark of " + passMark.stripTrailingZeros().toPlainString() + "%. Enter the score.");
        return new Decision(score.compareTo(passMark) >= 0, null);
    }
}
