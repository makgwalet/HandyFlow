package za.co.handyflow.platform.warehousing.application.internal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

/**
 * Stock threshold rules, with no I/O. The minimum and maximum for an item in a warehouse are the warehouse's own values where it has them and the item's
 * defaults otherwise, value by value; with neither there is nothing to alert on.
 *
 * Quantities are kept apart so that "100 on the floor" is never mistaken for "100 you can sell":
 * physical is everything on hand in the warehouse; unavailable is the part of it that is quarantined, damaged or blocked; reserved is what open orders hold of
 * the sellable stock; available is sellable on hand less reserved; incoming is what open shipments still expect.
 */
public final class WhseStockAlertRules {
    private WhseStockAlertRules() {}

    /**
     * OUT_OF_STOCK: nothing available although a minimum is set. BELOW_MIN: available is under the minimum. ABOVE_MAX: sellable stock on hand is over the maximum
     * (this is also what "overstock" means here).
     */
    public enum Kind { OUT_OF_STOCK, BELOW_MIN, ABOVE_MAX }

    public enum Source { WAREHOUSE, ITEM }

    public record Levels(BigDecimal min, Source minSource, BigDecimal max, Source maxSource) {
        public boolean any() { return min != null || max != null; }
    }

    public record Figures(BigDecimal physical, BigDecimal unavailable, BigDecimal reserved, BigDecimal available, BigDecimal incoming, BigDecimal sellableOnHand) {
        /** From the three sums a warehouse holds: all on hand, sellable on hand (AVAILABLE status) and what is allocated of it, plus what is incoming. */
        public static Figures of(BigDecimal onHand, BigDecimal sellableOnHand, BigDecimal allocated, BigDecimal incoming) {
            BigDecimal oh = nz(onHand), sell = nz(sellableOnHand), res = nz(allocated);
            return new Figures(oh, oh.subtract(sell).max(BigDecimal.ZERO), res, sell.subtract(res).max(BigDecimal.ZERO), nz(incoming), sell);
        }
    }

    public record Finding(Kind kind, BigDecimal recommendedOrder) {}

    public static Levels resolve(BigDecimal warehouseMin, BigDecimal warehouseMax, BigDecimal itemMin, BigDecimal itemMax) {
        return new Levels(warehouseMin != null ? warehouseMin : itemMin, warehouseMin != null ? Source.WAREHOUSE : itemMin != null ? Source.ITEM : null,
                warehouseMax != null ? warehouseMax : itemMax, warehouseMax != null ? Source.WAREHOUSE : itemMax != null ? Source.ITEM : null);
    }

    /** The alert for the figures, if any. Below the minimum takes precedence over above the maximum, which cannot both be true for sane levels anyway. */
    public static Optional<Finding> classify(Levels levels, Figures f) {
        if (levels == null || !levels.any()) return Optional.empty();
        BigDecimal min = levels.min(), max = levels.max();
        if (min != null && min.signum() > 0 && f.available().signum() == 0) return Optional.of(new Finding(Kind.OUT_OF_STOCK, recommend(levels, f)));
        if (min != null && f.available().compareTo(min) < 0) return Optional.of(new Finding(Kind.BELOW_MIN, recommend(levels, f)));
        if (max != null && f.sellableOnHand().compareTo(max) > 0) return Optional.of(new Finding(Kind.ABOVE_MAX, null));
        return Optional.empty();
    }

    /**
     * What to order: up to the maximum (or the minimum when there is no maximum) from what is available and already on its way. Zero when incoming stock already
     * covers it, so a shipment that is on its way does not produce a second order.
     */
    public static BigDecimal recommend(Levels levels, Figures f) {
        BigDecimal target = levels.max() != null ? levels.max() : levels.min();
        if (target == null) return BigDecimal.ZERO;
        BigDecimal need = target.subtract(f.available()).subtract(f.incoming());
        return need.signum() < 0 ? BigDecimal.ZERO : need.setScale(3, RoundingMode.HALF_UP);
    }

    /** What is wrong with a warehouse threshold, or null: at least one level, whole non-negative amounts to 3 decimals, and a maximum above the minimum. */
    public static String problem(BigDecimal min, BigDecimal max) {
        if (min == null && max == null) return "Enter a minimum, a maximum, or both.";
        if (min != null && min.signum() < 0) return "The minimum cannot be negative.";
        if (max != null && max.signum() <= 0) return "The maximum must be above zero.";
        BigDecimal cap = new BigDecimal("999999999");
        if ((min != null && (min.compareTo(cap) > 0 || min.scale() > 3)) || (max != null && (max.compareTo(cap) > 0 || max.scale() > 3)))
            return "Levels have at most 3 decimals and must be below a billion.";
        if (min != null && max != null && max.compareTo(min) <= 0) return "The maximum must be more than the minimum.";
        return null;
    }

    /** As {@link #problem}, for the levels that would result once the item's defaults fill in what the warehouse leaves blank. */
    public static String problemAfterDefaults(BigDecimal warehouseMin, BigDecimal warehouseMax, BigDecimal itemMin, BigDecimal itemMax) {
        Levels l = resolve(warehouseMin, warehouseMax, itemMin, itemMax);
        if (l.min() != null && l.max() != null && l.max().compareTo(l.min()) <= 0)
            return "With the item's own " + (l.minSource() == Source.ITEM ? "minimum" : "maximum") + " filling the blank, the maximum would not be above the minimum.";
        return null;
    }

    private static BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }
}
