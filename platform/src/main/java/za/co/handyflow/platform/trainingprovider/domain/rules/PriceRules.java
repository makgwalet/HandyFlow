package za.co.handyflow.platform.trainingprovider.domain.rules;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

/**
 * Programme price books. The provider enters every fee and says where it comes from; nothing is seeded and no VAT rate is held. A line is never
 * edited: a changed fee is a new line and the old one is withdrawn with a reason. Invoicing only ever shows a line as a suggestion.
 */
public final class PriceRules {

    public static final Set<String> FEE_TYPES = Set.of("TUITION", "REGISTRATION", "ASSESSMENT", "MATERIALS", "CERTIFICATION", "OTHER");
    public static final String OTHER = "OTHER";
    public static final BigDecimal MAX_AMOUNT = BigDecimal.valueOf(1_000_000_000L);
    public static final int MIN_NOTE = 10;

    private PriceRules() {
    }

    public static String problem(String feeType, String description, BigDecimal amount, Boolean includesVat, LocalDate from, LocalDate to, String sourceNote) {
        if (feeType == null || !FEE_TYPES.contains(feeType)) {
            return "Choose the kind of fee";
        }
        if (OTHER.equals(feeType) && (description == null || description.trim().length() < 2)) {
            return "Say what the fee is for";
        }
        if (amount == null || amount.signum() <= 0 || amount.compareTo(MAX_AMOUNT) > 0) {
            return "The amount must be more than 0";
        }
        if (amount.stripTrailingZeros().scale() > 2) {
            return "The amount can have at most two decimal places";
        }
        if (includesVat == null) {
            return "Say whether the amount includes VAT";
        }
        if (from == null) {
            return "Give the day the fee starts";
        }
        if (to != null && to.isBefore(from)) {
            return "The end date cannot be before the start date";
        }
        if (sourceNote == null || sourceNote.trim().length() < MIN_NOTE) {
            return "Say where the fee comes from, in at least " + MIN_NOTE + " characters";
        }
        return null;
    }

    public static String withdrawProblem(String reason) {
        return reason != null && reason.trim().length() >= MIN_NOTE ? null : "Give a reason of at least " + MIN_NOTE + " characters";
    }

    /** Two periods overlap when each starts on or before the day the other ends. A missing end date is open-ended. */
    public static boolean overlaps(LocalDate from1, LocalDate to1, LocalDate from2, LocalDate to2) {
        boolean firstStartsBeforeSecondEnds = to2 == null || !from1.isAfter(to2);
        boolean secondStartsBeforeFirstEnds = to1 == null || !from2.isAfter(to1);
        return firstStartsBeforeSecondEnds && secondStartsBeforeFirstEnds;
    }

    public static boolean inForce(LocalDate from, LocalDate to, LocalDate on) {
        return !on.isBefore(from) && (to == null || !on.isAfter(to));
    }

    /** True when two lines are for the same fee: same type, and for OTHER the same description ignoring case. */
    public static boolean sameFee(String type1, String desc1, String type2, String desc2) {
        if (!type1.equals(type2)) {
            return false;
        }
        if (!OTHER.equals(type1)) {
            return true;
        }
        return desc1 != null && desc2 != null && desc1.trim().equalsIgnoreCase(desc2.trim());
    }
}
