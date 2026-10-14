package za.co.handyflow.platform.accounting.application.internal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Pure rules for the fixed-asset register and depreciation (finance F25). No framework, no database.
 * Convention: depreciation starts in the month of acquisition (a full month), is charged monthly, and an asset
 * is not depreciated in the month it is disposed of. Every figure is a cumulative total, so a month that was
 * missed (an asset added late, a run skipped) is caught up in the next run instead of being lost.
 */
public final class FixedAssetRules {
    private FixedAssetRules() {}

    public static final String STRAIGHT_LINE = "STRAIGHT_LINE", REDUCING_BALANCE = "REDUCING_BALANCE";
    public static final Set<String> METHODS = Set.of(STRAIGHT_LINE, REDUCING_BALANCE);
    public static final int MAX_LIFE_MONTHS = 600;

    private static BigDecimal r2(BigDecimal v) { return v.setScale(2, RoundingMode.HALF_UP); }

    public static String saveProblem(String name, LocalDate acquired, BigDecimal cost, BigDecimal residual,
                                     String method, Integer lifeMonths, BigDecimal ratePercent, LocalDate today) {
        if (name == null || name.isBlank()) return "Give the asset a name.";
        if (name.trim().length() > 160) return "The name is too long (160 characters at most).";
        if (acquired == null) return "Enter the date it was acquired.";
        if (acquired.isAfter(today)) return "The acquisition date cannot be in the future.";
        if (cost == null || cost.signum() <= 0) return "Cost must be more than zero.";
        if (cost.stripTrailingZeros().scale() > 2) return "Cost has at most two decimals.";
        if (residual == null || residual.signum() < 0) return "Residual value cannot be negative.";
        if (residual.compareTo(cost) >= 0) return "Residual value must be less than the cost.";
        if (method == null || !METHODS.contains(method)) return "Choose straight line or reducing balance.";
        if (STRAIGHT_LINE.equals(method)) {
            if (lifeMonths == null || lifeMonths < 1 || lifeMonths > MAX_LIFE_MONTHS) return "Useful life must be 1 to " + MAX_LIFE_MONTHS + " months.";
        } else {
            if (ratePercent == null || ratePercent.signum() <= 0 || ratePercent.compareTo(BigDecimal.valueOf(100)) > 0)
                return "The yearly rate must be more than 0 and at most 100 percent.";
        }
        return null;
    }

    /** Months charged up to and including {@code period} (first of a month): 1 in the acquisition month, 0 before it. */
    public static int monthsThrough(LocalDate acquired, LocalDate period) {
        long m = ChronoUnit.MONTHS.between(acquired.withDayOfMonth(1), period.withDayOfMonth(1)) + 1;
        return (int) Math.max(0, m);
    }

    /** Total depreciation after {@code months} monthly charges, never more than cost less residual. */
    public static BigDecimal cumulative(String method, BigDecimal cost, BigDecimal residual, Integer lifeMonths, BigDecimal ratePercent, int months) {
        BigDecimal depreciable = cost.subtract(residual);
        if (months <= 0) return BigDecimal.ZERO.setScale(2);
        BigDecimal total;
        if (STRAIGHT_LINE.equals(method)) {
            if (months >= lifeMonths) return r2(depreciable);
            total = depreciable.multiply(BigDecimal.valueOf(months)).divide(BigDecimal.valueOf(lifeMonths), 2, RoundingMode.HALF_UP);
        } else {
            // Reducing balance: each month takes rate/12 of what is left. cost x (1 - (1 - r/12)^n)
            double monthly = ratePercent.doubleValue() / 100.0 / 12.0;
            double left = Math.pow(1.0 - monthly, months);
            total = r2(cost.multiply(BigDecimal.valueOf(1.0 - left)));
        }
        return total.min(r2(depreciable)).max(BigDecimal.ZERO.setScale(2));
    }

    /** What to charge for {@code period} given what has been charged so far. */
    public static BigDecimal chargeFor(String method, BigDecimal cost, BigDecimal residual, Integer lifeMonths, BigDecimal ratePercent,
                                       LocalDate acquired, LocalDate period, BigDecimal accumulatedSoFar) {
        BigDecimal target = cumulative(method, cost, residual, lifeMonths, ratePercent, monthsThrough(acquired, period));
        BigDecimal charge = target.subtract(accumulatedSoFar == null ? BigDecimal.ZERO : accumulatedSoFar);
        return charge.signum() > 0 ? r2(charge) : BigDecimal.ZERO.setScale(2);
    }

    public static LocalDate firstOfMonth(LocalDate d) { return d.withDayOfMonth(1); }
    public static LocalDate lastOfMonth(LocalDate period) { return period.withDayOfMonth(period.lengthOfMonth()); }

    /** A run is for one whole month, not in the future, and after the latest run already made. */
    public static String runProblem(LocalDate period, LocalDate today, LocalDate lastRunPeriod) {
        if (period == null) return "Choose the month.";
        if (period.getDayOfMonth() != 1) return "A depreciation run is for a whole month; use the first day of the month.";
        if (period.isAfter(firstOfMonth(today))) return "That month has not started yet.";
        if (lastRunPeriod != null && !period.isAfter(lastRunPeriod)) return "Depreciation has already been run for that month. Runs go in order.";
        return null;
    }

    public static LocalDate nextPeriod(LocalDate lastRunPeriod, LocalDate earliestAcquisition, LocalDate today) {
        LocalDate next = lastRunPeriod != null ? lastRunPeriod.plusMonths(1) : (earliestAcquisition != null ? firstOfMonth(earliestAcquisition) : firstOfMonth(today));
        return next.isAfter(firstOfMonth(today)) ? null : next;
    }

    // ── journal lines ────────────────────────────────────────────────────────

    public record Charge(UUID assetId, String assetNumber, UUID expenseAccountId, UUID accumulatedAccountId, UUID costCentreId, BigDecimal amount) {}
    public record JLine(UUID accountId, String description, BigDecimal debit, BigDecimal credit, UUID costCentreId) {}

    /** One debit per asset (so each can carry its cost centre) and one credit per accumulated-depreciation account. */
    public static List<JLine> runLines(List<Charge> charges, String label) {
        List<JLine> out = new ArrayList<>();
        Map<UUID, BigDecimal> credits = new LinkedHashMap<>();
        for (Charge c : charges) {
            if (c.amount().signum() <= 0) continue;
            out.add(new JLine(c.expenseAccountId(), "Depreciation " + c.assetNumber() + " " + label, c.amount(), null, c.costCentreId()));
            credits.merge(c.accumulatedAccountId(), c.amount(), BigDecimal::add);
        }
        credits.forEach((acc, amt) -> out.add(new JLine(acc, "Accumulated depreciation " + label, null, amt, null)));
        return out;
    }

    public static BigDecimal gainOrLoss(BigDecimal proceeds, BigDecimal cost, BigDecimal accumulated) {
        return r2(proceeds.subtract(cost.subtract(accumulated)));
    }

    public static String disposalProblem(String status, LocalDate acquired, LocalDate disposedOn, BigDecimal proceeds,
                                         UUID proceedsAccount, UUID gainLossAccount, LocalDate lastRunPeriod, LocalDate today) {
        if (!"ACTIVE".equals(status)) return "This asset has already been disposed of.";
        if (disposedOn == null) return "Enter the disposal date.";
        if (disposedOn.isAfter(today)) return "The disposal date cannot be in the future.";
        if (disposedOn.isBefore(acquired)) return "The disposal date is before the asset was acquired.";
        if (proceeds == null || proceeds.signum() < 0) return "Proceeds cannot be negative.";
        if (proceeds.signum() > 0 && proceedsAccount == null) return "Choose the account the proceeds were received into.";
        if (gainLossAccount == null) return "Choose the account for the gain or loss on disposal.";
        LocalDate needed = firstOfMonth(disposedOn).minusMonths(1);
        if (!firstOfMonth(acquired).isAfter(needed) && (lastRunPeriod == null || lastRunPeriod.isBefore(needed)))
            return "Run depreciation up to " + needed.getMonth() + " " + needed.getYear() + " first, so the book value is right.";
        return null;
    }

    public static List<JLine> disposalLines(UUID costAccount, UUID accumulatedAccount, UUID proceedsAccount, UUID gainLossAccount,
                                            BigDecimal cost, BigDecimal accumulated, BigDecimal proceeds, UUID costCentreId, String label) {
        List<JLine> out = new ArrayList<>();
        if (proceeds.signum() > 0) out.add(new JLine(proceedsAccount, "Proceeds " + label, proceeds, null, null));
        if (accumulated.signum() > 0) out.add(new JLine(accumulatedAccount, "Clear accumulated depreciation " + label, accumulated, null, null));
        out.add(new JLine(costAccount, "Remove cost " + label, null, cost, null));
        BigDecimal gl = gainOrLoss(proceeds, cost, accumulated);
        if (gl.signum() > 0) out.add(new JLine(gainLossAccount, "Gain on disposal " + label, null, gl, costCentreId));
        else if (gl.signum() < 0) out.add(new JLine(gainLossAccount, "Loss on disposal " + label, gl.negate(), null, costCentreId));
        return out;
    }

    /** Difference between the register and the ledger for one account; a positive number means the ledger holds more. */
    public static BigDecimal difference(BigDecimal ledger, BigDecimal register) {
        return r2((ledger == null ? BigDecimal.ZERO : ledger).subtract(register == null ? BigDecimal.ZERO : register));
    }
}
