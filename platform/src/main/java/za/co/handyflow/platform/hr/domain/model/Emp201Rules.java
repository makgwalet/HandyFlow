package za.co.handyflow.platform.hr.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;

/**
 * Filing and paying the monthly EMP201. Pure, so it runs without a database.
 * <p>
 * DRAFT (made by the pay run), SUBMITTED (filed on SARS eFiling, which gives the payment reference), PAID (paid in full).
 */
public final class Emp201Rules {

    public static final String DRAFT = "DRAFT", SUBMITTED = "SUBMITTED", PAID = "PAID";

    private Emp201Rules() {}

    /** The 7th of the month after the period, or the Friday before when that is a weekend. Public holidays are not allowed for. */
    public static LocalDate dueDate(LocalDate periodEnd) {
        LocalDate d = periodEnd.withDayOfMonth(1).plusMonths(1).withDayOfMonth(7);
        if (d.getDayOfWeek() == DayOfWeek.SUNDAY) return d.minusDays(2);
        if (d.getDayOfWeek() == DayOfWeek.SATURDAY) return d.minusDays(1);
        return d;
    }

    public static String cleanReference(String ref) {
        return ref == null ? "" : ref.replaceAll("\\s", "");
    }

    /** @param runIssued the pay run behind it is finalised; true too when there is no run */
    public static String submitProblem(String status, boolean runIssued, String reference) {
        if (!DRAFT.equals(status)) return "This declaration is already " + status.toLowerCase() + ".";
        if (!runIssued) return "Finalise the pay run before filing its EMP201.";
        String r = cleanReference(reference);
        if (r.isEmpty()) return "Enter the payment reference SARS gave when you filed.";
        if (!r.matches("[A-Za-z0-9]{6,30}")) return "The payment reference must be 6 to 30 letters or digits.";
        return null;
    }

    public static String payProblem(String status, LocalDate paidOn, BigDecimal amount, BigDecimal totalPayable, LocalDate periodEnd, LocalDate today) {
        if (DRAFT.equals(status)) return "File the EMP201 first. SARS gives the payment reference when you do.";
        if (PAID.equals(status)) return "This declaration is already paid.";
        if (paidOn == null) return "Enter the date it was paid.";
        if (paidOn.isAfter(today)) return "The payment date cannot be in the future.";
        if (paidOn.isBefore(periodEnd)) return "The payment date cannot be before the period ended.";
        if (amount == null || amount.signum() <= 0) return "Enter the amount paid.";
        if (amount.scale() > 2) return "The amount can have at most two decimals.";
        if (amount.setScale(2, RoundingMode.HALF_UP).compareTo(totalPayable.setScale(2, RoundingMode.HALF_UP)) != 0)
            return "The amount paid must equal the total payable, R" + totalPayable.setScale(2, RoundingMode.HALF_UP).toPlainString()
                    + ". Part payments are not recorded here.";
        return null;
    }

    public static boolean overdue(String status, LocalDate dueDate, LocalDate today) {
        return !PAID.equals(status) && dueDate != null && dueDate.isBefore(today);
    }

    /** Paid after the due date, so SARS may charge a penalty and interest. */
    public static boolean late(LocalDate paidOn, LocalDate dueDate) {
        return paidOn != null && dueDate != null && paidOn.isAfter(dueDate);
    }
}
