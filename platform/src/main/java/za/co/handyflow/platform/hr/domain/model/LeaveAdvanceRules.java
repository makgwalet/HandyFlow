package za.co.handyflow.platform.hr.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Taking leave taken in advance (a negative annual leave balance) back from final pay. A deduction from pay needs the employee's written
 * agreement (BCEA section 34), so none is made without one being named and dated. Pure.
 */
public final class LeaveAdvanceRules {

    private LeaveAdvanceRules() {}

    /** The description of the recovery, which is also how a second one is recognised. */
    public static final String DESCRIPTION = "Leave taken in advance";
    public static final int MAX_REF = 100;

    /** Null when the recovery may be entered. */
    public static String problem(String status, BigDecimal days, boolean alreadyEntered, String agreementRef, LocalDate agreementDate, LocalDate today) {
        if (!"TERMINATED".equals(status)) return "This employee is still employed. Leave taken in advance is only taken back when employment ends.";
        if (days == null || days.signum() <= 0) return "No annual leave was taken in advance.";
        if (alreadyEntered) return "A recovery for leave taken in advance is already entered for this employee.";
        if (agreementRef == null || agreementRef.isBlank())
            return "Name the employee's written agreement to this deduction. Pay may not be deducted without one (BCEA section 34).";
        if (agreementRef.trim().length() > MAX_REF) return "The agreement reference is too long (" + MAX_REF + " characters at most).";
        if (agreementDate == null) return "Give the date of the written agreement.";
        if (agreementDate.isAfter(today)) return "The agreement cannot be dated in the future.";
        return null;
    }

    /** The amount: the days at the employee's daily rate. */
    public static BigDecimal amount(BigDecimal monthlySalary, BigDecimal days) { return PayPeriodRules.leavePayout(monthlySalary, days); }

    public static String reason(BigDecimal days, String agreementRef, LocalDate agreementDate) {
        return days.stripTrailingZeros().toPlainString() + " day(s) of annual leave taken before it was earned. Written agreement: "
                + agreementRef.trim() + ", dated " + agreementDate + ".";
    }
}
