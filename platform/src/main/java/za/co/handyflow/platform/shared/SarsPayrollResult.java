package za.co.handyflow.platform.shared;

import java.math.BigDecimal;

/**
 * FIX: backlog 1.4 — the canonical PAYE/UIF/SDL result shape, shared by
 * hr.application.internal.PayrollEngine and
 * payrollbureau.application.internal.PayrollBureauEngine.
 * <p>
 * Field set matches hr.PayrollEngine's own (pre-consolidation) result
 * record exactly — it was the superset of the two (payrollbureau's own
 * result omitted taxBeforeRebate/primaryRebate/secondaryRebate/
 * tertiaryRebate/medicalTaxCredit, fields its own payslip PDF never
 * rendered). Each engine's local PayrollResult record is unchanged and
 * still what PayrollService/PayslipPdfGenerator and their payrollbureau
 * equivalents actually consume — this type exists only as the calculator's
 * output; each engine maps it into its own existing record on the way out,
 * so no caller outside the two engines themselves ever sees this type.
 */
public record SarsPayrollResult(
        BigDecimal grossSalary,
        BigDecimal travelAllowance,
        BigDecimal medicalAid,
        BigDecimal pension,
        BigDecimal payeAmount,
        BigDecimal uifEmployee,
        BigDecimal uifEmployer,
        BigDecimal sdlAmount,
        BigDecimal taxableIncome,
        BigDecimal taxBeforeRebate,
        BigDecimal primaryRebate,
        BigDecimal secondaryRebate,
        BigDecimal tertiaryRebate,
        BigDecimal medicalTaxCredit,
        int taxYear
) {
    public BigDecimal totalEarnings() {
        return grossSalary.add(travelAllowance);
    }

    public BigDecimal totalDeductions() {
        return payeAmount.add(uifEmployee).add(medicalAid).add(pension);
    }

    public BigDecimal netPay() {
        return totalEarnings().subtract(totalDeductions());
    }

    public BigDecimal totalRebates() {
        return primaryRebate.add(secondaryRebate).add(tertiaryRebate);
    }
}