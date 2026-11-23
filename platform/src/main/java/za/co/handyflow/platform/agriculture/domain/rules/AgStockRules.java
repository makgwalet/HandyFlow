package za.co.handyflow.platform.agriculture.domain.rules;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Stock rules: the average cost after a delivery, the direction of a stock-count correction, and who may receive or issue. Pure. */
public final class AgStockRules {

    public static final String ADJUSTMENT_UP = "ADJUSTMENT_UP";
    public static final String ADJUSTMENT_DOWN = "ADJUSTMENT_DOWN";
    public static final int MIN_REASON = 3;

    private AgStockRules() {}

    /**
     * Weighted average unit cost after receiving {@code receivedQty} at {@code receivedCost}. No cost given with the delivery leaves
     * the cost alone; nothing on hand (or no cost yet) takes the delivery's cost. Rounded to cents.
     */
    public static BigDecimal weightedAverageCost(BigDecimal onHandQty, BigDecimal onHandCost, BigDecimal receivedQty, BigDecimal receivedCost) {
        if (receivedCost == null) return onHandCost;
        if (receivedCost.signum() < 0) throw new IllegalArgumentException("The unit cost cannot be negative");
        if (onHandCost == null || onHandQty == null || onHandQty.signum() <= 0) return receivedCost.setScale(2, RoundingMode.HALF_UP);
        BigDecimal value = onHandQty.multiply(onHandCost).add(receivedQty.multiply(receivedCost));
        return value.divide(onHandQty.add(receivedQty), 2, RoundingMode.HALF_UP);
    }

    /** ADJUSTMENT_UP when the count is higher than the books, ADJUSTMENT_DOWN when lower. Same quantity is not an adjustment. */
    public static String adjustmentType(BigDecimal before, BigDecimal after) {
        int c = after.compareTo(before);
        if (c == 0) throw new IllegalArgumentException("The new quantity is the same as what is on hand");
        return c > 0 ? ADJUSTMENT_UP : ADJUSTMENT_DOWN;
    }

    /** How much the count moved, always positive; the direction is in {@link #adjustmentType}. */
    public static BigDecimal adjustmentQuantity(BigDecimal before, BigDecimal after) {
        return after.subtract(before).abs();
    }

    public static String requireReason(String notes) {
        String n = notes == null ? "" : notes.trim();
        if (n.length() < MIN_REASON) throw new IllegalArgumentException("Give a reason for the stock adjustment (at least " + MIN_REASON + " characters)");
        return n;
    }

    /** An inactive item cannot be received into or issued from. */
    public static void requireUsable(String status, String itemName) {
        if ("INACTIVE".equals(status)) throw new IllegalStateException(itemName + " is inactive. Reactivate it before receiving or issuing stock");
    }
}
