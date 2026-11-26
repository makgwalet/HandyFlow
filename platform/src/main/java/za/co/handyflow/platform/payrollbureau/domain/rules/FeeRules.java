package za.co.handyflow.platform.payrollbureau.domain.rules;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Set;

/** Pricing and profitability rules (phase 6). Pure. */
public final class FeeRules {

    public static final String PER_RUN = "PER_RUN";
    public static final String MONTHLY = "MONTHLY";
    public static final String ANNUAL = "ANNUAL";
    public static final Set<String> BILLINGS = Set.of(PER_RUN, MONTHLY, ANNUAL);

    private FeeRules() {}

    /** The processing line of a fee note. {@code minimumApplied} is true when the minimum fee, not the per-employee total, is charged. */
    public record RunFee(boolean minimumApplied, BigDecimal quantity, BigDecimal unitPrice, BigDecimal amount) {}

    public static RunFee runFee(BigDecimal perEmployee, int employees, BigDecimal minimum) {
        BigDecimal total = perEmployee.multiply(BigDecimal.valueOf(employees)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal min = minimum == null ? BigDecimal.ZERO : minimum.setScale(2, RoundingMode.HALF_UP);
        if (min.compareTo(total) > 0) return new RunFee(true, BigDecimal.ONE, min, min);
        return new RunFee(false, BigDecimal.valueOf(employees), perEmployee, total);
    }

    /** A per-run charge goes on every fee note; a monthly or annual one only if it has not been billed yet in that period. */
    public static boolean chargeDue(String billing, boolean alreadyBilledInPeriod) {
        return PER_RUN.equals(billing) || !alreadyBilledInPeriod;
    }

    /** First day of the period a charge's "already billed" check looks at, for an invoice date. */
    public static LocalDate periodStart(String billing, LocalDate invoiceDate) {
        if (ANNUAL.equals(billing)) return invoiceDate.withDayOfYear(1);
        return invoiceDate.withDayOfMonth(1);
    }

    public static LocalDate periodEnd(String billing, LocalDate invoiceDate) {
        if (ANNUAL.equals(billing)) return invoiceDate.withDayOfYear(invoiceDate.lengthOfYear());
        return invoiceDate.withDayOfMonth(invoiceDate.lengthOfMonth());
    }

    public static String chargeProblem(String description, BigDecimal amount, String billing) {
        if (description == null || description.isBlank()) return "Describe the charge.";
        if (description.trim().length() > 200) return "The description is at most 200 characters.";
        if (amount == null || amount.signum() <= 0) return "The amount must be more than zero.";
        if (amount.compareTo(new BigDecimal("1000000")) > 0) return "That amount looks too large.";
        if (billing == null || !BILLINGS.contains(billing)) return "Billing must be PER_RUN, MONTHLY or ANNUAL.";
        return null;
    }

    public static String pricingProblem(BigDecimal perEmployee, BigDecimal minimum) {
        if (perEmployee == null || perEmployee.signum() < 0) return "The per-employee fee cannot be negative.";
        if (minimum == null || minimum.signum() < 0) return "The minimum fee cannot be negative.";
        if (perEmployee.compareTo(new BigDecimal("100000")) > 0 || minimum.compareTo(new BigDecimal("1000000")) > 0) return "That fee looks too large.";
        return null;
    }

    public static String cancelProblem(String status, BigDecimal amountPaid, String reason) {
        if ("CANCELLED".equals(status)) return "This fee note is already cancelled.";
        if (amountPaid != null && amountPaid.signum() > 0) return "A fee note with payments recorded cannot be cancelled.";
        if (reason == null || reason.isBlank()) return "Say why this fee note is being cancelled.";
        if (reason.trim().length() > 500) return "The reason is at most 500 characters.";
        return null;
    }

    /** Margin as a percentage of revenue, or null when nothing was billed. */
    public static BigDecimal marginPercent(BigDecimal revenue, BigDecimal cost) {
        if (revenue == null || revenue.signum() == 0) return null;
        return revenue.subtract(cost).multiply(new BigDecimal("100")).divide(revenue, 1, RoundingMode.HALF_UP);
    }
}
