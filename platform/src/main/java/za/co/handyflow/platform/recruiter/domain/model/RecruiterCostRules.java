package za.co.handyflow.platform.recruiter.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/** What a recorded hiring cost must look like, and how a cost per hire is worked out. Pure. */
public final class RecruiterCostRules {
    private RecruiterCostRules() {}

    public static final BigDecimal MAX_AMOUNT = new BigDecimal("10000000");
    public static final int MAX_NOTE = 200;
    public static final Map<String, String> CATEGORIES = new LinkedHashMap<>();
    static {
        CATEGORIES.put("ADVERTISING", "Advertising");
        CATEGORIES.put("AGENCY_FEE", "Agency fee");
        CATEGORIES.put("ASSESSMENT", "Assessments");
        CATEGORIES.put("BACKGROUND_CHECK", "Background checks");
        CATEGORIES.put("OTHER", "Other");
    }

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

    /** Cost divided by hires, to the cent. Null when nobody was hired, so a job with no hire never shows a made-up figure. */
    public static BigDecimal costPerHire(BigDecimal totalCost, long hires) {
        if (totalCost == null || hires <= 0) return null;
        return totalCost.divide(BigDecimal.valueOf(hires), 2, java.math.RoundingMode.HALF_UP);
    }
}
