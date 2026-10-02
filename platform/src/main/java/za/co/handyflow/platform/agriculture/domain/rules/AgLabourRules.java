package za.co.handyflow.platform.agriculture.domain.rules;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Labour costing rules (ADR-001, W3): turns an HR salary into an hourly rate, loads it with the employer's on-costs, and prices the hours worked.
 * <p>
 * An employee's gross salary is for one PAY PERIOD, so the hourly rate is that salary divided by the ordinary hours in the period: a weekly
 * wage covers one week of ordinary hours, a fortnightly one two weeks, and a monthly salary 52/12 weeks (the average month, so a month is not
 * 4 or 5 weeks depending on the calendar). The weekly hours default to 45, the BCEA ordinary maximum, and are a tenant setting.
 * <p>
 * The rate is snapshotted into the cost ledger when the labour is costed, so later raises never rewrite past costs. Pure and dependency-free
 * so the arithmetic is unit tested without Spring or a database.
 */
public final class AgLabourRules {

    private AgLabourRules() {}

    /** The cost ledger's source type for labour costed from work records (the rate came from HR, or was typed in for a casual worker). */
    public static final String SOURCE_TYPE = "HR_LABOUR";
    public static final BigDecimal DEFAULT_HOURS_PER_WEEK = new BigDecimal("45.00");
    public static final BigDecimal DEFAULT_ON_COST_PERCENT = new BigDecimal("0.00");
    /** More than this many ordinary hours a week is not a plausible setting. */
    public static final BigDecimal MAX_HOURS_PER_WEEK = new BigDecimal("84");

    private static final BigDecimal WEEKS_PER_YEAR = new BigDecimal("52");
    private static final BigDecimal MONTHS_PER_YEAR = new BigDecimal("12");
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    /** Ordinary hours in one pay period, or null for a pay frequency this does not know (HR allows MONTHLY, WEEKLY and FORTNIGHTLY). */
    public static BigDecimal hoursPerPayPeriod(String payFrequency, BigDecimal hoursPerWeek) {
        if (payFrequency == null || hoursPerWeek == null || hoursPerWeek.signum() <= 0) return null;
        return switch (payFrequency.trim().toUpperCase(java.util.Locale.ROOT)) {
            case "WEEKLY" -> hoursPerWeek;
            case "FORTNIGHTLY" -> hoursPerWeek.multiply(BigDecimal.valueOf(2));
            case "MONTHLY" -> hoursPerWeek.multiply(WEEKS_PER_YEAR).divide(MONTHS_PER_YEAR, 6, RoundingMode.HALF_UP);
            default -> null;
        };
    }

    /** The base hourly rate (before on-costs) a gross salary works out to, to four places; null when it cannot be worked out. */
    public static BigDecimal hourlyRate(BigDecimal grossSalary, String payFrequency, BigDecimal hoursPerWeek) {
        if (grossSalary == null || grossSalary.signum() <= 0) return null;
        BigDecimal hours = hoursPerPayPeriod(payFrequency, hoursPerWeek);
        if (hours == null) return null;
        return grossSalary.divide(hours, 4, RoundingMode.HALF_UP);
    }

    /** The base rate plus the employer's on-costs (UIF, SDL and so on) as a percentage of it, to four places. This is what is snapshotted. */
    public static BigDecimal loadedRate(BigDecimal baseRate, BigDecimal onCostPercent) {
        BigDecimal pct = onCostPercent == null ? BigDecimal.ZERO : onCostPercent;
        return baseRate.multiply(BigDecimal.ONE.add(pct.divide(HUNDRED, 8, RoundingMode.HALF_UP))).setScale(4, RoundingMode.HALF_UP);
    }

    /**
     * The cost of the hours worked, to the cent, from the SNAPSHOTTED rate, so that amount = hours x rate always reproduces exactly from the two
     * numbers stored in the ledger.
     */
    public static BigDecimal amount(BigDecimal hours, BigDecimal loadedRate) {
        return hours.multiply(loadedRate).setScale(2, RoundingMode.HALF_UP);
    }

    /** @throws IllegalArgumentException when the weekly hours or the on-cost percentage are outside a sensible range */
    public static void requireValidSettings(BigDecimal hoursPerWeek, BigDecimal onCostPercent) {
        if (hoursPerWeek == null || hoursPerWeek.signum() <= 0 || hoursPerWeek.compareTo(MAX_HOURS_PER_WEEK) > 0) {
            throw new IllegalArgumentException("standard hours per week must be above 0 and at most " + MAX_HOURS_PER_WEEK.toPlainString());
        }
        if (onCostPercent == null || onCostPercent.signum() < 0 || onCostPercent.compareTo(HUNDRED) > 0) {
            throw new IllegalArgumentException("labour on-cost must be between 0 and 100 percent");
        }
    }
}
