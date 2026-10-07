package za.co.handyflow.platform.clinic.application.internal;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * What the scheme paid on a claim. A full payment may leave the amount out (it is the claim total);
 * a partial payment must say how much was received, because there is no sensible default.
 */
final class ClaimPaymentRules {

    private ClaimPaymentRules() {}

    /**
     * The amount to record for a PAID or PARTIAL action.
     *
     * @throws IllegalArgumentException when the amount is missing for PARTIAL, not above zero,
     *         above the claim total, or (for PARTIAL) not below the claim total
     */
    static BigDecimal schemeAmount(String action, BigDecimal gross, BigDecimal supplied) {
        BigDecimal total = gross == null ? BigDecimal.ZERO : gross;
        BigDecimal amount = supplied == null ? null : supplied.setScale(2, RoundingMode.HALF_UP);
        switch (action) {
            case "PAID" -> {
                if (amount == null) return total;
                requirePositive(amount);
                if (amount.compareTo(total) > 0) throw new IllegalArgumentException(
                        "The scheme amount (R " + amount + ") is more than the claim total (R " + total + ")");
                return amount;
            }
            case "PARTIAL" -> {
                if (amount == null) throw new IllegalArgumentException(
                        "Enter the amount the scheme paid for a partial payment");
                requirePositive(amount);
                if (amount.compareTo(total) >= 0) throw new IllegalArgumentException(
                        "A partial payment must be less than the claim total (R " + total + "); use Mark paid for a full payment");
                return amount;
            }
            default -> throw new IllegalArgumentException("Not a payment action: " + action);
        }
    }

    private static void requirePositive(BigDecimal amount) {
        if (amount.signum() <= 0) throw new IllegalArgumentException("The scheme amount must be more than zero");
    }
}
