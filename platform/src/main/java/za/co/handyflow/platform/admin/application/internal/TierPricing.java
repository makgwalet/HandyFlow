package za.co.handyflow.platform.admin.application.internal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Pure pricing rules for module tiers. The same rules serve every module, so no module needs its own billing engine.
 * Nothing here reads prices from anywhere: callers pass the price history in.
 */
public final class TierPricing {

    private TierPricing() {}

    public enum Period { MONTHLY, ANNUAL }

    public enum ChangeKind { NEW, UPGRADE, DOWNGRADE, SAME_TIER }

    /** One row of the price book. amount null = negotiated per tenant. */
    public record Price(String tierCode, Period period, BigDecimal amount, LocalDate effectiveFrom, boolean approved) {}

    /**
     * The price to use on a date: approved only, already in effect, latest effective date wins.
     * An unapproved price is never returned, which is what keeps customer charges off until approval.
     */
    public static Optional<Price> effective(List<Price> book, String tierCode, Period period, LocalDate on) {
        return book.stream()
                .filter(Price::approved)
                .filter(p -> p.tierCode().equals(tierCode) && p.period() == period)
                .filter(p -> !p.effectiveFrom().isAfter(on))
                .max(Comparator.comparing(Price::effectiveFrom));
    }

    public static ChangeKind classify(Integer currentSort, int newSort) {
        if (currentSort == null) return ChangeKind.NEW;
        if (newSort > currentSort) return ChangeKind.UPGRADE;
        if (newSort < currentSort) return ChangeKind.DOWNGRADE;
        return ChangeKind.SAME_TIER;
    }

    /**
     * When a change takes effect. An upgrade takes effect on the requested date.
     * A downgrade is controlled: it never takes effect before the end of the period already paid for.
     */
    public static LocalDate changeEffective(ChangeKind kind, LocalDate requested, LocalDate currentPeriodEnd) {
        if (kind == ChangeKind.DOWNGRADE && currentPeriodEnd != null && requested.isBefore(currentPeriodEnd)) {
            return currentPeriodEnd;
        }
        return requested;
    }

    /** Start of the billing period (calendar month for monthly, anniversary year for annual) holding the date. */
    public static LocalDate periodStart(Period period, LocalDate subscriptionStart, LocalDate on) {
        if (period == Period.MONTHLY) return on.withDayOfMonth(1);
        long years = ChronoUnit.YEARS.between(subscriptionStart, on);
        return subscriptionStart.plusYears(Math.max(years, 0));
    }

    /** Exclusive end of that period. */
    public static LocalDate periodEnd(Period period, LocalDate subscriptionStart, LocalDate on) {
        LocalDate start = periodStart(period, subscriptionStart, on);
        return period == Period.MONTHLY ? start.plusMonths(1) : start.plusYears(1);
    }

    /**
     * Charge for the unused part of the period when upgrading mid-period: the price difference only,
     * for the days remaining. Never negative.
     */
    public static BigDecimal prorateUpgrade(BigDecimal oldAmount, BigDecimal newAmount,
                                            LocalDate changeDate, LocalDate periodStart, LocalDate periodEnd) {
        if (oldAmount == null || newAmount == null) return BigDecimal.ZERO;
        long total = ChronoUnit.DAYS.between(periodStart, periodEnd);
        long remaining = ChronoUnit.DAYS.between(changeDate, periodEnd);
        if (total <= 0 || remaining <= 0) return BigDecimal.ZERO;
        BigDecimal diff = newAmount.subtract(oldAmount);
        if (diff.signum() <= 0) return BigDecimal.ZERO;
        return diff.multiply(BigDecimal.valueOf(remaining))
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    }

    /**
     * What a subscription bills in an invoice month. Monthly bills every month. Annual bills the full year in the
     * month the subscription starts and on each anniversary, and nothing in between.
     */
    public static BigDecimal chargeForMonth(Period period, BigDecimal amount, LocalDate subscriptionStart, YearMonth month) {
        if (amount == null) return BigDecimal.ZERO;
        if (period == Period.MONTHLY) return amount;
        YearMonth start = YearMonth.from(subscriptionStart);
        long months = ChronoUnit.MONTHS.between(start, month);
        return months >= 0 && months % 12 == 0 ? amount : BigDecimal.ZERO;
    }

    /** True when an annual subscription is paid for this month but not billed (shown as a prepaid line). */
    public static boolean prepaid(Period period, LocalDate subscriptionStart, YearMonth month) {
        return period == Period.ANNUAL
                && chargeForMonth(period, BigDecimal.ONE, subscriptionStart, month).signum() == 0
                && !month.isBefore(YearMonth.from(subscriptionStart));
    }
}
