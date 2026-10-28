package za.co.handyflow.platform.hr.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Severance pay for an employee let go for operational reasons (retrenchment). Pure rules, no database.
 * <p>
 * BCEA section 41: at least one week's remuneration for each completed year of service, unless the employee unreasonably refused
 * alternative employment with the employer. Tax: severance is a lump sum for which the employer must obtain a SARS directive
 * (IRP3(a)) whatever the amount. SARS works out the tax; it is recorded here and taken from the payment, and it is reported under code
 * 4115 beside the severance under code 3901. Notice pay and leave pay are not severance and are taxed on their own.
 */
public final class SeveranceRules {

    private SeveranceRules() {}

    public static final String DESCRIPTION = "Severance pay";
    public static final int MAX_DIRECTIVE = 40;

    /** Whole years from the start date to the last day. */
    public static int completedYears(LocalDate start, LocalDate lastDay) {
        if (start == null || lastDay == null || lastDay.isBefore(start)) return 0;
        return (int) ChronoUnit.YEARS.between(start, lastDay);
    }

    /** A week's pay is a year's pay over 52. */
    public static BigDecimal weeklyPay(BigDecimal monthlySalary) {
        if (monthlySalary == null || monthlySalary.signum() <= 0) return BigDecimal.ZERO;
        return monthlySalary.multiply(BigDecimal.valueOf(12)).divide(BigDecimal.valueOf(52), 2, RoundingMode.HALF_UP);
    }

    /** The least the BCEA requires: a week's pay for each completed year. */
    public static BigDecimal statutoryMinimum(BigDecimal monthlySalary, LocalDate start, LocalDate lastDay) {
        return weeklyPay(monthlySalary).multiply(BigDecimal.valueOf(completedYears(start, lastDay))).setScale(2, RoundingMode.HALF_UP);
    }

    /** Null when the directive number can be kept. */
    public static String directiveProblem(String number) {
        if (number == null || number.isBlank()) return "Enter the SARS directive number. Severance cannot be paid without a tax directive.";
        String n = number.trim();
        if (n.length() > MAX_DIRECTIVE) return "The directive number is too long (" + MAX_DIRECTIVE + " characters at most).";
        if (!n.matches("[A-Za-z0-9/\\- ]+")) return "The directive number has letters, digits, slashes and dashes only.";
        return null;
    }

    /**
     * Null when severance may be entered.
     *
     * @param minimum the BCEA minimum for the years served
     * @param refused the employee unreasonably refused alternative employment, which removes the minimum
     */
    public static String problem(String employeeStatus, String endType, BigDecimal amount, String directive, BigDecimal directiveTax,
                                 boolean refused, BigDecimal minimum, boolean alreadyEntered) {
        if (!"TERMINATED".equals(employeeStatus)) return "Severance is entered once employment has ended.";
        if (endType == null) return "Record how employment ended first. Severance is owed when the employer ends it for operational reasons.";
        if (!NoticeRules.RETRENCHMENT.equals(endType))
            return "Employment ended by " + NoticeRules.typeLabel(endType) + ", which is not a retrenchment, so no severance is owed. Change how it ended if that was wrong.";
        if (alreadyEntered) return "Severance is already entered for this employee.";
        if (amount == null || amount.signum() <= 0) return "Enter the severance amount.";
        if (amount.scale() > 2) return "Use at most two decimals for the amount.";
        String d = directiveProblem(directive);
        if (d != null) return d;
        if (directiveTax == null || directiveTax.signum() < 0) return "Enter the tax SARS directed on the severance (nil if it directed none).";
        if (directiveTax.scale() > 2) return "Use at most two decimals for the tax.";
        if (directiveTax.compareTo(amount) > 0) return "The directed tax cannot be more than the severance.";
        if (!refused && minimum != null && amount.compareTo(minimum) < 0)
            return "That is less than the BCEA minimum of " + minimum + " (a week's pay for each completed year). "
                    + "Pay at least that, or tick that the employee unreasonably refused alternative employment.";
        return null;
    }

    /** What the employee receives of it. */
    public static BigDecimal net(BigDecimal amount, BigDecimal tax) {
        BigDecimal a = amount == null ? BigDecimal.ZERO : amount;
        BigDecimal t = tax == null ? BigDecimal.ZERO : tax;
        return a.subtract(t);
    }

    /** One plain paragraph for the person ending employment. Null when it is not a retrenchment. */
    public static String summary(String endType, BigDecimal monthlySalary, LocalDate start, LocalDate lastDay) {
        if (!NoticeRules.RETRENCHMENT.equals(endType)) return null;
        int years = completedYears(start, lastDay);
        if (years == 0) return "Retrenchment with under a year of service: the BCEA minimum severance is nil. Apply to SARS for a directive for any severance paid.";
        return "Severance owed at least: " + statutoryMinimum(monthlySalary, start, lastDay) + " (a week's pay for each of " + years
                + " completed year" + (years == 1 ? "" : "s") + "). Apply to SARS for a tax directive before paying it.";
    }
}
