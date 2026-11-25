package za.co.handyflow.platform.complianceservices.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Set;

/**
 * The arithmetic of practice profitability, with no database in it. Money here is professional revenue excluding VAT. Cost is labour only:
 * hours worked times what each person's hour costs. It is not a full margin, because external costs and overhead are not held here.
 */
public final class ProfitRules {

    private ProfitRules() {}

    public static final BigDecimal MAX_RATE = new BigDecimal("10000");
    /** Invoices in these states count as billed. A draft has not been issued and a cancelled invoice never happened. */
    public static final Set<String> BILLED = Set.of("ISSUED", "PARTIALLY_PAID", "PAID", "OVERPAID", "OVERDUE");

    public record Rate(LocalDate effectiveFrom, BigDecimal hourlyCost) {}

    public record Revenue(BigDecimal invoiced, BigDecimal collected) {
        public static Revenue none() { return new Revenue(BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2)); }
        public Revenue add(Revenue o) { return new Revenue(invoiced.add(o.invoiced), collected.add(o.collected)); }
    }

    public static BigDecimal money(BigDecimal v) { return v.setScale(2, RoundingMode.HALF_UP); }

    public static BigDecimal checkRate(BigDecimal hourlyCost) {
        if (hourlyCost == null || hourlyCost.signum() <= 0) throw new IllegalArgumentException("An hour must cost more than nothing");
        if (hourlyCost.compareTo(MAX_RATE) > 0) throw new IllegalArgumentException("That is more than R 10,000 an hour. Check the amount");
        return money(hourlyCost);
    }

    /** The rate in force on a day: the latest one that started on or before it. Null when none had started yet. */
    public static BigDecimal rateOn(Collection<Rate> rates, LocalDate day) {
        Rate best = null;
        for (Rate r : rates) {
            if (r.effectiveFrom().isAfter(day)) continue;
            if (best == null || r.effectiveFrom().isAfter(best.effectiveFrom())) best = r;
        }
        return best == null ? null : best.hourlyCost();
    }

    public static BigDecimal costOf(int minutes, BigDecimal hourlyCost) {
        return money(hourlyCost.multiply(BigDecimal.valueOf(minutes)).divide(BigDecimal.valueOf(60), 4, RoundingMode.HALF_UP));
    }

    /**
     * What a bill is worth without VAT, and how much of that has been paid. An invoice carries VAT, so the bill's amount is scaled by the
     * share of the invoice that was credited and the share that was paid. A bill that is not issued, or has no invoice, is worth nothing yet.
     */
    public static Revenue revenueOf(BigDecimal amount, String invoiceStatus, BigDecimal total, BigDecimal paid, BigDecimal credited) {
        if (amount == null || invoiceStatus == null || !BILLED.contains(invoiceStatus) || total == null || total.signum() <= 0) return Revenue.none();
        BigDecimal credit = credited == null ? BigDecimal.ZERO : credited.min(total);
        BigDecimal net = total.subtract(credit);
        BigDecimal invoiced = amount.multiply(net).divide(total, 4, RoundingMode.HALF_UP);
        BigDecimal pay = paid == null ? BigDecimal.ZERO : paid.max(BigDecimal.ZERO).min(net);
        BigDecimal collected = amount.multiply(pay).divide(total, 4, RoundingMode.HALF_UP);
        return new Revenue(money(invoiced), money(collected));
    }

    /** Margin as a percentage of what was invoiced, or null when nothing was invoiced. It can be negative. */
    public static BigDecimal marginPct(BigDecimal invoiced, BigDecimal cost) {
        if (invoiced == null || invoiced.signum() <= 0) return null;
        return invoiced.subtract(cost).multiply(BigDecimal.valueOf(100)).divide(invoiced, 1, RoundingMode.HALF_UP);
    }

    /** Billable hours as a percentage of all hours recorded, or null when none were recorded. */
    public static BigDecimal utilisationPct(int billableMinutes, int minutes) {
        if (minutes <= 0) return null;
        return BigDecimal.valueOf(billableMinutes).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(minutes), 1, RoundingMode.HALF_UP);
    }
}
