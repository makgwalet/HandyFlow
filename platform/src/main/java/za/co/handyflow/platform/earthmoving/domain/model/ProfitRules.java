package za.co.handyflow.platform.earthmoving.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * How the profitability report works things out. Revenue is hire charges that have not been voided (invoiced or not),
 * counted in the period their hire starts. Cost is the cost on service records performed in the dates. It is a
 * margin on hire against maintenance only: fuel, operators, depreciation and finance costs are not in it.
 */
public final class ProfitRules {
    private ProfitRules() {}

    public static final long MAX_DAYS = 731;

    public static void checkRange(LocalDate from, LocalDate to) {
        if (from == null || to == null) throw new IllegalArgumentException("Give a start and an end date.");
        if (to.isBefore(from)) throw new IllegalArgumentException("The end date is before the start date.");
        if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_DAYS) throw new IllegalArgumentException("Choose a period of at most two years.");
    }

    public static BigDecimal margin(BigDecimal revenue, BigDecimal cost) {
        return nz(revenue).subtract(nz(cost));
    }

    /** Margin as a percentage of revenue, one decimal place; null when there was no revenue (a percentage would mean nothing). */
    public static BigDecimal marginPercent(BigDecimal revenue, BigDecimal margin) {
        if (revenue == null || revenue.signum() <= 0) return null;
        return margin.multiply(BigDecimal.valueOf(100)).divide(revenue, 1, RoundingMode.HALF_UP);
    }

    private static BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }
}
