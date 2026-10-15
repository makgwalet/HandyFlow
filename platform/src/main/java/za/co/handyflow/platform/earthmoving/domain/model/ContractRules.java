package za.co.handyflow.platform.earthmoving.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Map;

/**
 * When a hire is expected to be covered by a signed contract. A company sets a value from which a contract is expected; below it
 * the standard terms apply. With no value set, contracts are never expected. A missing or unsigned contract is a warning
 * only: it never stops a deployment.
 */
public final class ContractRules {
    private ContractRules() {}

    public static final String REF_TYPE = "EARTHMOVING_DEPLOYMENT";
    public static final String HIRE_TYPE = "EQUIPMENT_HIRE";
    public static final String ADDENDUM_TYPE = "EQUIPMENT_HIRE_ADDENDUM";
    public static final String SIGNED = "SIGNED";

    private static final Map<String, String> STATUS_WORDS = Map.of(
            "DRAFT", "still a draft", "UNDER_REVIEW", "under review", "SENT", "sent but not signed yet",
            "EXPIRED", "expired", "TERMINATED", "terminated", "ARCHIVED", "archived");

    /** Daily rate times the days of the hire, both ends counted; null when the rate or either date is missing. */
    public static BigDecimal expectedValue(BigDecimal dailyRate, LocalDate from, LocalDate to) {
        if (dailyRate == null || from == null || to == null || to.isBefore(from)) return null;
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        return dailyRate.multiply(BigDecimal.valueOf(days)).setScale(2, RoundingMode.HALF_UP);
    }

    /** A threshold is a rand amount of zero or more; null switches the expectation off. */
    public static BigDecimal checkThreshold(BigDecimal t) {
        if (t == null) return null;
        if (t.signum() < 0) throw new IllegalArgumentException("The contract threshold cannot be negative.");
        if (t.compareTo(new BigDecimal("1000000000")) > 0) throw new IllegalArgumentException("The contract threshold is too large.");
        return t.setScale(2, RoundingMode.HALF_UP);
    }

    public static boolean isExpected(BigDecimal threshold, BigDecimal value) {
        return threshold != null && value != null && value.compareTo(threshold) >= 0;
    }

    /** What to tell the person, or null when nothing needs saying. {@code status} is the linked contract's status, null when none is linked. */
    public static String warning(BigDecimal threshold, BigDecimal value, String contractNumber, String status) {
        if (!isExpected(threshold, value)) return null;
        if (status == null)
            return "This hire is worth about R" + money(value) + ", which is at or above the R" + money(threshold)
                    + " from which a signed contract is expected, and no contract is linked to it.";
        if (SIGNED.equals(status)) return null;
        return "The hire contract " + (contractNumber == null ? "" : contractNumber + " ") + "is "
                + STATUS_WORDS.getOrDefault(status, status.toLowerCase().replace('_', ' ')) + ", not signed.";
    }

    public static String money(BigDecimal v) { return v.setScale(2, RoundingMode.HALF_UP).toPlainString(); }

    /** A short extension agreement. Text is escaped because it ends up in a document people sign. */
    public static String addendumBody(String contractNumber, String machine, LocalDate oldEnd, LocalDate newEnd, String reason) {
        return "<h2>ADDENDUM TO HIRE AGREEMENT " + esc(contractNumber) + "</h2>"
                + "<p>The parties agree to extend the hire of " + esc(machine) + " under the agreement above"
                + (oldEnd == null ? "" : " from its current end date of " + oldEnd) + " to <strong>" + newEnd + "</strong>.</p>"
                + "<p>Reason: " + esc(reason) + "</p>"
                + "<p>All other terms of the hire agreement, including the daily rate, remain unchanged.</p>";
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
