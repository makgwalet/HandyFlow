package za.co.handyflow.platform.supplychain.application.internal;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Pure replenishment logic. An item needs reordering when what it has (available + already ordered + already requested)
 * falls to the level it needs to cover the lead time plus a safety margin at its recent rate of use, or to its reorder
 * point, whichever is higher. The quantity brings it back up to its maximum level, or, without one, to the reorder level
 * plus a month of use (and at least the item's reorder quantity), in whole reorder quantities.
 */
public final class ReplenishmentRules {
    private ReplenishmentRules() {}

    public static final int DEFAULT_LEAD_TIME_DAYS = 7;
    public static final int SAFETY_DAYS = 3;
    public static final int MONTH_DAYS = 30;

    public enum Urgency { STOCKOUT, CRITICAL, REORDER }

    public record Input(BigDecimal available, BigDecimal onOrder, BigDecimal requested, BigDecimal reorderPoint, BigDecimal reorderQty,
                        BigDecimal maxLevel, BigDecimal dailyDemand, int leadTimeDays) {}

    public record Recommendation(BigDecimal reorderLevel, BigDecimal position, BigDecimal orderQty, Urgency urgency, BigDecimal daysCover, String reason) {}

    /** @return null when no order is needed (or there is nothing to base one on) */
    public static Recommendation recommend(Input in) {
        BigDecimal available = nz(in.available()), onOrder = nz(in.onOrder()), requested = nz(in.requested());
        BigDecimal rp = nz(in.reorderPoint()), rq = nz(in.reorderQty()), max = nz(in.maxLevel()), daily = nz(in.dailyDemand());
        int lead = in.leadTimeDays() > 0 ? in.leadTimeDays() : DEFAULT_LEAD_TIME_DAYS;

        BigDecimal demandLevel = ceil(daily.multiply(BigDecimal.valueOf(lead + SAFETY_DAYS)));
        BigDecimal level = rp.max(demandLevel);
        if (level.signum() <= 0) return null;

        BigDecimal position = available.add(onOrder).add(requested);
        if (position.compareTo(level) > 0) return null;

        BigDecimal target;
        boolean capped = max.compareTo(level) > 0;
        if (capped) target = max;
        else target = level.add(rq.max(ceil(daily.multiply(BigDecimal.valueOf(MONTH_DAYS)))));
        BigDecimal qty = target.subtract(position);
        if (!capped && rq.signum() > 0) qty = ceil(qty.divide(rq, 6, RoundingMode.HALF_UP)).multiply(rq);
        qty = ceil(qty);
        if (qty.signum() <= 0) return null;

        BigDecimal cover = daily.signum() > 0 ? available.divide(daily, 1, RoundingMode.DOWN) : null;
        Urgency urgency = available.signum() <= 0 ? Urgency.STOCKOUT
                : cover != null && cover.compareTo(BigDecimal.valueOf(lead)) < 0 ? Urgency.CRITICAL : Urgency.REORDER;

        String why = demandLevel.compareTo(rp) > 0
                ? "At the recent rate of use (" + daily.setScale(2, RoundingMode.HALF_UP).toPlainString() + "/day) the " + lead + "-day lead time plus " + SAFETY_DAYS + " days' margin needs "
                        + demandLevel.stripTrailingZeros().toPlainString() + " in hand or on order; there is " + position.stripTrailingZeros().toPlainString()
                : "At or below the reorder point of " + rp.stripTrailingZeros().toPlainString() + " (" + position.stripTrailingZeros().toPlainString() + " in hand or on order)";
        return new Recommendation(level, position, qty, urgency, cover, why);
    }

    private static BigDecimal ceil(BigDecimal v) { return v.setScale(0, RoundingMode.CEILING); }
    private static BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }
}
