package za.co.handyflow.platform.supplychain.application.internal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;

/** Pure arithmetic and judgement for the spend, savings and supplier-performance views. */
public final class AnalyticsRules {
    private AnalyticsRules() {}

    public static final int LOW_SCORE = 60;
    public static final double SIGNIFICANT_SPEND_SHARE_PCT = 5.0;

    /** part / total as a percentage to one decimal place; null when there is no total to compare against. */
    public static Double sharePct(BigDecimal part, BigDecimal total) {
        if (part == null || total == null || total.signum() <= 0) return null;
        return part.multiply(BigDecimal.valueOf(100)).divide(total, 1, RoundingMode.HALF_UP).doubleValue();
    }

    /** Share of the total taken by the n biggest amounts; null with no total. */
    public static Double topShare(List<BigDecimal> amounts, int n, BigDecimal total) {
        BigDecimal top = amounts.stream().sorted(Comparator.reverseOrder()).limit(n).reduce(BigDecimal.ZERO, BigDecimal::add);
        return sharePct(top, total);
    }

    /** A saving only means something against a positive baseline. Negative when the award cost more than the baseline. */
    public static BigDecimal saving(BigDecimal baseline, BigDecimal actual) {
        if (baseline == null || actual == null || baseline.signum() <= 0) return null;
        return baseline.subtract(actual);
    }

    /** "HIGH_SPEND_LOW_SCORE" (act first), "LOW_SCORE", or null. A missing score is never flagged: no data is not poor performance. */
    public static String supplierFlag(Integer score, Double spendSharePct) {
        if (score == null || score >= LOW_SCORE) return null;
        return spendSharePct != null && spendSharePct >= SIGNIFICANT_SPEND_SHARE_PCT ? "HIGH_SPEND_LOW_SCORE" : "LOW_SCORE";
    }

    /** Overpayment on one line: the unit price above the contract's, times the quantity; zero when at or under it. */
    public static BigDecimal overContract(BigDecimal unitCost, BigDecimal agreed, BigDecimal qty) {
        if (unitCost == null || agreed == null || qty == null || unitCost.compareTo(agreed) <= 0) return BigDecimal.ZERO;
        return unitCost.subtract(agreed).multiply(qty).setScale(2, RoundingMode.HALF_UP);
    }
}
