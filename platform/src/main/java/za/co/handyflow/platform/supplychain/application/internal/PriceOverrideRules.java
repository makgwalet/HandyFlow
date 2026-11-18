package za.co.handyflow.platform.supplychain.application.internal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;

/**
 * What happens when a buyer orders at a different unit price from the requisition's estimate.
 * Pure and Spring-free, so it is tested without a context.
 *
 * <ul>
 *   <li>Same price: nothing to record.</li>
 *   <li>Lower price: allowed, and the difference is recorded as a saving.</li>
 *   <li>Higher, within the company's tolerance: allowed, with the reason on the audit trail.</li>
 *   <li>Higher, beyond tolerance: allowed to be raised, but a second person must approve it before the PO can be approved.</li>
 * </ul>
 * Any change needs a reason; "OTHER" also needs a note.
 */
public final class PriceOverrideRules {

    private PriceOverrideRules() {}

    public enum Kind { UNCHANGED, DECREASE, INCREASE_WITHIN_TOLERANCE, INCREASE_BEYOND_TOLERANCE }

    public static final List<String> REASONS = List.of("SUPPLIER_QUOTATION", "NEGOTIATED_DISCOUNT", "MARKET_PRICE_CHANGE",
            "CONTRACT_PRICE", "SPECIFICATION_CHANGE", "OTHER");

    /** changePct is relative to the estimate (null when the estimate is zero). saving is positive only for a decrease. */
    public record Assessment(Kind kind, BigDecimal changePerUnit, BigDecimal changePct, BigDecimal saving, boolean needsApproval) {
        public boolean changed() { return kind != Kind.UNCHANGED; }
    }

    public static Assessment assess(BigDecimal estimate, BigDecimal actual, BigDecimal qty, BigDecimal tolerancePct) {
        BigDecimal est = estimate != null ? estimate : BigDecimal.ZERO;
        int cmp = actual.compareTo(est);
        if (cmp == 0) return new Assessment(Kind.UNCHANGED, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, false);
        BigDecimal diff = actual.subtract(est);
        BigDecimal pct = est.signum() == 0 ? null
                : diff.multiply(BigDecimal.valueOf(100)).divide(est, 2, RoundingMode.HALF_UP);
        if (cmp < 0) {
            BigDecimal saving = est.subtract(actual).multiply(qty).setScale(2, RoundingMode.HALF_UP);
            return new Assessment(Kind.DECREASE, diff, pct, saving, false);
        }
        boolean within = pct != null && pct.compareTo(tolerancePct) <= 0;
        return new Assessment(within ? Kind.INCREASE_WITHIN_TOLERANCE : Kind.INCREASE_BEYOND_TOLERANCE, diff, pct,
                BigDecimal.ZERO, !within);
    }

    /** What is wrong with the reason for a price change, or null. An unchanged price needs none. */
    public static String reasonProblem(Assessment a, String reason, String note) {
        if (!a.changed()) return null;
        if (reason == null || reason.isBlank()) return "A reason is required when the unit price differs from the requisition estimate";
        if (!REASONS.contains(reason.trim().toUpperCase(Locale.ROOT))) return "Unknown price change reason '" + reason + "'";
        if ("OTHER".equalsIgnoreCase(reason.trim()) && (note == null || note.isBlank()))
            return "Explain the price change in the note when the reason is OTHER";
        return null;
    }
}
