package za.co.handyflow.platform.hr.application.internal;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.hr.domain.model.HrEmployee;
import za.co.handyflow.platform.shared.SarsPayeCalculator;
import za.co.handyflow.platform.shared.SarsPayrollResult;
import za.co.handyflow.platform.shared.StatutoryYearRules;

import java.math.BigDecimal;

/**
 * HR's payroll calculation: a thin adapter over {@link SarsPayeCalculator}, which takes its figures from the approved
 * rules of the tax year it is given. Nothing is hard-coded here, and there is no fallback when the rules are missing:
 * the caller gets them from {@link StatutoryRulesService}, which refuses.
 * <p>
 * This used to share its formula with the payroll bureau engine. It no longer does: the old formula gave the medical
 * tax credit to everyone, mislabelled the tax year and fell back to zero tax. The bureau engine still uses the old
 * shared calculator and needs its own review.
 */
@Slf4j
@Component
public class PayrollEngine {

    public PayrollResult calculate(HrEmployee employee, StatutoryYearRules rules, BigDecimal annualPayroll, int medicalMembers) {
        return calculate(employee, rules, annualPayroll, medicalMembers, BigDecimal.ZERO);
    }

    /**
     * With approved overtime for the month. Overtime is part of the month's remuneration: it is added to the salary the calculator
     * annualises, so PAYE, UIF (up to the ceiling) and SDL all see it, and it is reported separately from the basic salary.
     * <p>
     * Annualising a month that includes overtime taxes it as if it recurred every month. That is the periodic method used for the
     * rest of this engine; any difference is settled when the employee's year is assessed. The averaging method SARS also allows
     * for fluctuating pay is not implemented.
     */
    public PayrollResult calculate(HrEmployee employee, StatutoryYearRules rules, BigDecimal annualPayroll, int medicalMembers, BigDecimal overtime) {
        BigDecimal ot = overtime == null ? BigDecimal.ZERO : overtime;
        SarsPayrollResult r = SarsPayeCalculator.calculate(new SarsPayeCalculator.Inputs(
                employee.getGrossSalary().add(ot), employee.getTravelAllowance(), employee.getPensionContribution(),
                employee.getMedicalAidContribution(), employee.getDateOfBirth(), medicalMembers, annualPayroll), rules);

        log.debug("Payroll employee={} year={} taxable={} paye={} uif={} sdl={} medicalMembers={}",
                employee.getId(), rules.taxYear(), r.taxableIncome(), r.payeAmount(), r.uifEmployee(), r.sdlAmount(), medicalMembers);

        return new PayrollResult(
                r.grossSalary().subtract(ot), r.travelAllowance(), r.medicalAid(), r.pension(),
                r.payeAmount(), r.uifEmployee(), r.uifEmployer(), r.sdlAmount(),
                r.taxableIncome(), r.taxBeforeRebate(), r.primaryRebate(),
                r.secondaryRebate(), r.tertiaryRebate(), r.medicalTaxCredit(), r.taxYear(), ot);
    }

    public record PayrollResult(
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
            int taxYear,
            BigDecimal overtime
    ) {
        /** Without overtime. */
        public PayrollResult(BigDecimal grossSalary, BigDecimal travelAllowance, BigDecimal medicalAid, BigDecimal pension,
                             BigDecimal payeAmount, BigDecimal uifEmployee, BigDecimal uifEmployer, BigDecimal sdlAmount,
                             BigDecimal taxableIncome, BigDecimal taxBeforeRebate, BigDecimal primaryRebate,
                             BigDecimal secondaryRebate, BigDecimal tertiaryRebate, BigDecimal medicalTaxCredit, int taxYear) {
            this(grossSalary, travelAllowance, medicalAid, pension, payeAmount, uifEmployee, uifEmployer, sdlAmount, taxableIncome,
                    taxBeforeRebate, primaryRebate, secondaryRebate, tertiaryRebate, medicalTaxCredit, taxYear, BigDecimal.ZERO);
        }

        public BigDecimal totalEarnings() { return grossSalary.add(overtime).add(travelAllowance); }

        public BigDecimal totalDeductions() {
            // the medical credit is already inside payeAmount
            return payeAmount.add(uifEmployee).add(medicalAid).add(pension);
        }

        public BigDecimal netPay() { return totalEarnings().subtract(totalDeductions()); }

        public BigDecimal totalRebates() { return primaryRebate.add(secondaryRebate).add(tertiaryRebate); }
    }
}
