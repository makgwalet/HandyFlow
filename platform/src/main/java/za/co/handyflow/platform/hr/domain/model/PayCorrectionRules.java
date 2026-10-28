package za.co.handyflow.platform.hr.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Correcting a payslip that has been issued. The payslip and its pay run are never changed; the correction is a pay item that the next
 * run pays or recovers, linked to the payslip so the trail is kept. Pure rules, no database.
 * <ul>
 *   <li><b>UNDERPAID</b>: money the employee was owed and did not get. Entered as back pay for the period of the payslip, as a gross
 *       amount, and taxed with the next pay by the annual-payment method.</li>
 *   <li><b>OVERPAID</b>: money the employee was paid and should not have been. Entered as a recovery of the net amount, taken from net pay
 *       after tax. It is never more than the net pay of the payslip.</li>
 * </ul>
 * Tax already paid on an overpayment is not claimed back by this; it is settled when the employee's year is assessed.
 */
public final class PayCorrectionRules {

    private PayCorrectionRules() {}

    public static final String UNDERPAID = "UNDERPAID", OVERPAID = "OVERPAID";

    public static String kindFor(String direction) {
        return UNDERPAID.equals(direction) ? PayAdjustmentRules.BACK_PAY : OVERPAID.equals(direction) ? PayAdjustmentRules.RECOVERY : null;
    }

    public static String description(String payRunNumber) { return "Correction of payslip " + (payRunNumber == null ? "" : payRunNumber).trim(); }

    /** The period a back pay correction covers: the payslip's own period, kept inside the employee's service and not in the future. */
    public static LocalDate relatesFrom(LocalDate periodStart, LocalDate employeeStart) {
        return employeeStart != null && employeeStart.isAfter(periodStart) ? employeeStart : periodStart;
    }

    public static LocalDate relatesTo(LocalDate periodEnd, LocalDate today) { return periodEnd.isAfter(today) ? today : periodEnd; }

    /** Null when the correction may be entered. The pay-item rules (reason, amount scale, not for yourself) are applied after these. */
    public static String problem(String direction, BigDecimal amount, BigDecimal payslipNet, String runStatus, String employeeStatus, boolean sameKindWaiting) {
        if (kindFor(direction) == null) return "Say whether the employee was underpaid or overpaid.";
        if (!PayRunWorkflowRules.isIssued(runStatus) || PayRunWorkflowRules.CANCELLED.equals(runStatus))
            return "Only a payslip from a finalised pay run can be corrected this way. A run that is not finalised is changed by recalculating it.";
        if ("TERMINATED".equals(employeeStatus))
            return "This employee has left, so no later pay run will pay or recover this. Settle it with them directly and note it on the file.";
        if (amount == null || amount.signum() <= 0) return "Enter the amount.";
        if (OVERPAID.equals(direction) && payslipNet != null && amount.compareTo(payslipNet) > 0)
            return "The amount paid by mistake cannot be more than the net pay on the payslip (" + payslipNet.toPlainString() + ").";
        if (sameKindWaiting) return "A correction of this kind is already waiting for this payslip. Cancel it first if it is wrong.";
        return null;
    }
}
