package za.co.handyflow.platform.hr.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Bonuses, back pay and recoveries of overpayments: what may be entered, and who may take it back. Pure rules, no database.
 * <p>
 * A bonus is a lump sum. Back pay is money owed for an earlier period (for example a raise that was agreed late), so it must say
 * which period it covers. Both are taxed in the month they are paid, by the annual-payment method; that is a payroll-engine matter and
 * not decided here.
 */
public final class PayAdjustmentRules {

    private PayAdjustmentRules() {}

    public static final String BONUS = "BONUS", BACK_PAY = "BACK_PAY", RECOVERY = "RECOVERY", NOTICE_PAY = "NOTICE_PAY";
    public static final List<String> KINDS = List.of(BONUS, BACK_PAY, RECOVERY, NOTICE_PAY);
    public static final String OPEN = "OPEN", CANCELLED = "CANCELLED";
    public static final BigDecimal MAX_AMOUNT = new BigDecimal("10000000");
    /** How far ahead an item may be dated: a year is far enough for a planned bonus, and catches a mistyped year. */
    public static final int MAX_DAYS_AHEAD = 366;

    /** Null when an item may be entered. */
    public static String addProblem(String kind, BigDecimal amount, LocalDate payFrom, String description, String reason,
                                    LocalDate relatesFrom, LocalDate relatesTo, LocalDate today, LocalDate employmentStart,
                                    boolean forSelf) {
        if (forSelf) return "You cannot enter a payment for yourself. Ask another payroll user.";
        if (kind == null || !KINDS.contains(kind)) return "Choose bonus, back pay, notice pay or recovery of an overpayment.";
        if (amount == null || amount.signum() <= 0) return "Enter the amount.";
        if (amount.scale() > 2) return "Use at most two decimals for the amount.";
        if (amount.compareTo(MAX_AMOUNT) > 0) return "That amount is too large.";
        if (payFrom == null) return "Say from which pay month it should be paid.";
        if (payFrom.isAfter(today.plusDays(MAX_DAYS_AHEAD))) return "That date is more than a year away. Check the year.";
        if (description == null || description.isBlank()) return "Describe the payment (for example, \"Annual performance bonus 2026\").";
        if (description.trim().length() > 200) return "The description is too long (200 characters at most).";
        if (reason == null || reason.isBlank()) return "Give the reason for the payment.";
        if (reason.trim().length() > 300) return "The reason is too long (300 characters at most).";
        if (BACK_PAY.equals(kind)) {
            if (relatesFrom == null || relatesTo == null) return "Back pay must say which period it covers.";
            if (relatesTo.isBefore(relatesFrom)) return "The period cannot end before it starts.";
            if (relatesTo.isAfter(today)) return "Back pay covers a period that has already passed.";
            if (employmentStart != null && relatesFrom.isBefore(employmentStart))
                return "Back pay cannot start before the employee did (" + employmentStart + ").";
        } else if (relatesFrom != null || relatesTo != null) {
            return "Only back pay covers a period. Leave the dates out, or choose back pay.";
        }
        return null;
    }

    /** Null when an item may be cancelled. */
    public static String cancelProblem(String status, boolean takenByRun, String reason) {
        if (CANCELLED.equals(status)) return "This payment has already been cancelled.";
        if (takenByRun) return "A pay run has already taken this payment. Send that run back to draft or cancel it first.";
        if (reason == null || reason.isBlank()) return "Give the reason for cancelling.";
        if (reason.trim().length() > 300) return "The reason is too long (300 characters at most).";
        return null;
    }

    /**
     * Which recoveries a pay can bear. A recovery is taken from net pay after tax, in the order entered, and only whole: one that would
     * take net pay below zero is left open for a later run instead of being taken in part. Later, smaller ones may still fit.
     */
    public static List<Boolean> affordable(List<BigDecimal> amounts, BigDecimal netPay) {
        List<Boolean> out = new java.util.ArrayList<>();
        BigDecimal left = netPay == null ? BigDecimal.ZERO : netPay;
        for (BigDecimal a : amounts) {
            boolean fits = a != null && a.signum() > 0 && left.compareTo(a) >= 0;
            if (fits) left = left.subtract(a);
            out.add(fits);
        }
        return out;
    }

    public static String label(String kind) {
        return switch (kind == null ? "" : kind) {
            case BONUS -> "Bonus";
            case BACK_PAY -> "Back pay";
            case RECOVERY -> "Recovery of overpayment";
            case NOTICE_PAY -> "Pay in place of notice";
            default -> String.valueOf(kind);
        };
    }
}
