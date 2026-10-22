package za.co.handyflow.platform.hr.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Employer-paid benefits that are taxable but not paid in cash: the employer's contribution to a medical scheme or a retirement fund,
 * and other fringe benefits whose taxable value HR works out. Pure rules: which lines apply in a month, and whether a line may be added.
 */
public final class BenefitRules {

    private BenefitRules() {}

    public static final String EMPLOYER_MEDICAL = "EMPLOYER_MEDICAL", EMPLOYER_RETIREMENT = "EMPLOYER_RETIREMENT", OTHER_FRINGE = "OTHER_FRINGE";
    public static final List<String> KINDS = List.of(EMPLOYER_MEDICAL, EMPLOYER_RETIREMENT, OTHER_FRINGE);
    public static final BigDecimal MAX_MONTHLY = new BigDecimal("1000000");

    /** A line as far as the rules need it. */
    public record Line(String kind, BigDecimal monthlyAmount, LocalDate from, LocalDate to) {
        public boolean activeOn(LocalDate d) { return !d.isBefore(from) && (to == null || !d.isAfter(to)); }
        boolean overlaps(LocalDate otherFrom, LocalDate otherTo) {
            return !(otherTo != null && otherTo.isBefore(from)) && !(to != null && to.isBefore(otherFrom));
        }
    }

    /** The month's taxable benefits, before any part-period reduction. */
    public record Monthly(BigDecimal medical, BigDecimal retirement, BigDecimal other) {
        public static Monthly none() { return new Monthly(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO); }
        public boolean isEmpty() { return medical.signum() == 0 && retirement.signum() == 0 && other.signum() == 0; }
    }

    /** What applies on a date. Lines of one kind do not overlap, but if they ever did the amounts add. */
    public static Monthly on(List<Line> lines, LocalDate date) {
        BigDecimal m = BigDecimal.ZERO, r = BigDecimal.ZERO, o = BigDecimal.ZERO;
        for (Line l : lines) {
            if (!l.activeOn(date)) continue;
            switch (l.kind()) {
                case EMPLOYER_MEDICAL -> m = m.add(l.monthlyAmount());
                case EMPLOYER_RETIREMENT -> r = r.add(l.monthlyAmount());
                case OTHER_FRINGE -> o = o.add(l.monthlyAmount());
                default -> { }
            }
        }
        return new Monthly(m, r, o);
    }

    /**
     * Null when a line may be added. Medical and retirement lines cannot overlap another line of the same kind (end the old one first); other
     * fringe benefits may run side by side, each with a description.
     */
    public static String addProblem(String kind, BigDecimal amount, LocalDate from, LocalDate to, String description, String reason,
                                    List<Line> existing, LocalDate employmentStart) {
        if (kind == null || !KINDS.contains(kind)) return "Choose the kind of benefit.";
        if (amount == null || amount.signum() <= 0) return "Enter the monthly amount.";
        if (amount.scale() > 2) return "Use at most two decimals for the amount.";
        if (amount.compareTo(MAX_MONTHLY) > 0) return "That amount is too large.";
        if (from == null) return "Say from which date it applies.";
        if (to != null && to.isBefore(from)) return "The end date cannot be before the start date.";
        if (employmentStart != null && from.isBefore(employmentStart)) return "A benefit cannot start before the employee did (" + employmentStart + ").";
        if (OTHER_FRINGE.equals(kind) && (description == null || description.isBlank()))
            return "Describe the benefit (for example, the company car), so the payslip and the certificate can be checked.";
        if (description != null && description.trim().length() > 200) return "The description is too long (200 characters at most).";
        if (reason == null || reason.isBlank()) return "Give the reason for the benefit or the change.";
        if (reason.trim().length() > 300) return "The reason is too long (300 characters at most).";
        if (!OTHER_FRINGE.equals(kind))
            for (Line l : existing) if (kind.equals(l.kind()) && l.overlaps(from, to))
                return "This employee already has this benefit for part of that time. End it first, then add the new amount.";
        return null;
    }

    /** Null when a line may be ended on the given date. */
    public static String endProblem(LocalDate from, LocalDate currentEnd, LocalDate end) {
        if (end == null) return "Say the last day the benefit applies.";
        if (end.isBefore(from)) return "The last day cannot be before the benefit started (" + from + ").";
        if (currentEnd != null) return "This benefit has already been ended.";
        return null;
    }

    public static String label(String kind) {
        return switch (kind == null ? "" : kind) {
            case EMPLOYER_MEDICAL -> "Employer medical scheme contribution";
            case EMPLOYER_RETIREMENT -> "Employer retirement fund contribution";
            case OTHER_FRINGE -> "Other taxable benefit";
            default -> String.valueOf(kind);
        };
    }
}
