package za.co.handyflow.platform.accountant.application.internal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** Pure rules for work in progress (time not yet billed) and for how much of the standard value was billed. */
public final class WipRules {
    private WipRules() {}

    public record Entry(LocalDate date, BigDecimal hours, BigDecimal rate) {}
    public record Aged(BigDecimal hours, BigDecimal value, BigDecimal current, BigDecimal days31to60, BigDecimal days61to90, BigDecimal over90, LocalDate oldest) {}

    public static BigDecimal value(Entry e) {
        return e.hours().multiply(e.rate()).setScale(2, RoundingMode.HALF_UP);
    }

    /** Value by age: 0 to 30 days, 31 to 60, 61 to 90, over 90. */
    public static Aged age(List<Entry> entries, LocalDate today) {
        BigDecimal hours = BigDecimal.ZERO, total = BigDecimal.ZERO, a = BigDecimal.ZERO, b = BigDecimal.ZERO, c = BigDecimal.ZERO, d = BigDecimal.ZERO;
        LocalDate oldest = null;
        for (Entry e : entries) {
            long days = Math.max(0, ChronoUnit.DAYS.between(e.date(), today));
            BigDecimal v = value(e);
            hours = hours.add(e.hours());
            total = total.add(v);
            if (days <= 30) a = a.add(v); else if (days <= 60) b = b.add(v); else if (days <= 90) c = c.add(v); else d = d.add(v);
            if (oldest == null || e.date().isBefore(oldest)) oldest = e.date();
        }
        return new Aged(hours, total, a, b, c, d, oldest);
    }

    /** Billed as a share of the standard value of the time, in percent. Null when there was no time. */
    public static BigDecimal realisation(BigDecimal billed, BigDecimal standardValue) {
        if (standardValue == null || standardValue.signum() <= 0) return null;
        return billed.multiply(new BigDecimal("100")).divide(standardValue, 1, RoundingMode.HALF_UP);
    }

    /** Hours used as a share of the hours budgeted. Null when nothing was budgeted. */
    public static BigDecimal budgetUsed(BigDecimal hours, BigDecimal budget) {
        if (budget == null || budget.signum() <= 0) return null;
        return hours.multiply(new BigDecimal("100")).divide(budget, 0, RoundingMode.HALF_UP);
    }

    public static String periodProblem(LocalDate from, LocalDate to) {
        if (from == null || to == null) return "Choose both dates.";
        if (to.isBefore(from)) return "The end date is before the start date.";
        if (ChronoUnit.DAYS.between(from, to) > 800) return "Choose a period of at most about two years.";
        return null;
    }
}
