package za.co.handyflow.platform.payrollbureau.application.internal;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.payrollbureau.domain.model.PayEmployee;
import za.co.handyflow.platform.payrollbureau.domain.model.Payslip;
import za.co.handyflow.platform.payrollbureau.domain.rules.RulesFingerprint;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.SarsPayeCalculator;
import za.co.handyflow.platform.shared.SarsPayrollResult;
import za.co.handyflow.platform.shared.StatutoryRulesProvider;
import za.co.handyflow.platform.shared.StatutoryYearRules;

import java.math.BigDecimal;

/**
 * Payroll Bureau's calculation: a thin adapter over the shared {@link SarsPayeCalculator}, with the figures taken from the
 * approved statutory rules of the tax year (through {@link StatutoryRulesProvider}, the same source HR uses). There are no rates
 * or year figures in this class, and no fallback to another year: a run for a year without approved rules is refused.
 * <p>
 * The bureau may not depend on the {@code hr} module; both depend on {@code shared}.
 */
@Slf4j
@Component
public class PayrollBureauEngine {

    private final StatutoryRulesProvider rulesProvider;

    public PayrollBureauEngine(StatutoryRulesProvider rulesProvider) {
        this.rulesProvider = rulesProvider;
    }

    /** Same shape the payslip PDF and the service layer have always used. */
    public record PayrollResult(
            BigDecimal monthlySalary, BigDecimal travelAllowance, BigDecimal medicalAid, BigDecimal pension,
            BigDecimal payeAmount, BigDecimal uifEmployee, BigDecimal uifEmployer, BigDecimal sdlAmount,
            BigDecimal taxableIncome, int taxYear
    ) {
        public BigDecimal totalEarnings() { return monthlySalary.add(travelAllowance); }
        public BigDecimal netPay() {
            return totalEarnings().subtract(payeAmount).subtract(uifEmployee)
                    .subtract(medicalAid).subtract(pension);
        }
    }

    /** The approved rules of the tax year, or a 422 telling the user to load and approve them. */
    public StatutoryYearRules requireRules(int taxYear) {
        try {
            return rulesProvider.rulesFor(taxYear);
        } catch (IllegalStateException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.UNPROCESSABLE_ENTITY, "NO_TAX_RULES");
        }
    }

    /** The bureau does not record medical scheme dependants yet, so the taxpayer's own credit is all it can give. */
    public int medicalMembers(PayEmployee employee) {
        BigDecimal medical = employee.getMedicalAidContribution();
        return medical != null && medical.signum() > 0 ? 1 : 0;
    }

    /** What UIF is charged on for this period's pay: capped at the monthly ceiling, less what earlier runs this month used. */
    public BigDecimal uifBase(PayEmployee employee, StatutoryYearRules rules, int periodsPerYear, BigDecimal uifBaseEarlierThisMonth) {
        BigDecimal pay = employee.getGrossSalary();
        return periodsPerYear == 12
                ? pay.min(rules.uifMonthlyCeiling())
                : SarsPayeCalculator.uifBase(pay, rules.uifMonthlyCeiling(), uifBaseEarlierThisMonth);
    }

    /** Records on the payslip which rules, formula and inputs produced its figures (slice 1b). */
    public void freeze(Payslip payslip, PayEmployee employee, StatutoryYearRules rules, BigDecimal annualPayroll) {
        freeze(payslip, employee, rules, annualPayroll, 12, BigDecimal.ZERO);
    }

    public void freeze(Payslip payslip, PayEmployee employee, StatutoryYearRules rules, BigDecimal annualPayroll,
                       int periodsPerYear, BigDecimal uifBaseEarlierThisMonth) {
        payslip.freeze(rules.effectiveFrom(), rules.effectiveTo(), RulesFingerprint.of(rules), RulesFingerprint.formulaFor(periodsPerYear),
                employee.getDateOfBirth(), medicalMembers(employee), annualPayroll);
        payslip.recordPeriodic(periodsPerYear, uifBase(employee, rules, periodsPerYear, uifBaseEarlierThisMonth));
    }

    /**
     * The year's payroll size for the SDL threshold: every active employee's pay per period times the periods a year of their
     * own pay group. {@code periodsOf} gives that count for a group id (null = the default monthly payroll).
     */
    public BigDecimal annualPayroll(java.util.List<PayEmployee> activeEmployees, java.util.function.Function<java.util.UUID, Integer> periodsOf) {
        BigDecimal total = BigDecimal.ZERO;
        for (PayEmployee e : activeEmployees) {
            int periods = e.getPayGroupId() == null ? 12 : periodsOf.apply(e.getPayGroupId());
            total = total.add(e.getGrossSalary().multiply(BigDecimal.valueOf(periods)));
        }
        return total;
    }

    /** Monthly pay: the amounts on the employee are a month's pay. */
    public PayrollResult calculate(PayEmployee employee, StatutoryYearRules rules, BigDecimal annualPayroll) {
        return calculate(employee, rules, annualPayroll, 12, BigDecimal.ZERO);
    }

    /**
     * Pay for one period of a pay group. The amounts on the employee are what that employee gets <em>per pay period of their group</em>
     * (a week's pay for a weekly group). {@code uifBaseEarlierThisMonth} is what UIF was already charged on in other runs of the same
     * calendar month; it only matters when there is more than one run a month.
     */
    public PayrollResult calculate(PayEmployee employee, StatutoryYearRules rules, BigDecimal annualPayroll,
                                   int periodsPerYear, BigDecimal uifBaseEarlierThisMonth) {
        int medicalMembers = medicalMembers(employee);
        SarsPayrollResult r = SarsPayeCalculator.calculate(new SarsPayeCalculator.Inputs(
                employee.getGrossSalary(), employee.getTravelAllowance(), employee.getPensionContribution(),
                employee.getMedicalAidContribution(), employee.getDateOfBirth(), medicalMembers, annualPayroll),
                rules, periodsPerYear, uifBaseEarlierThisMonth);

        log.debug("Payroll bureau employee={} year={} taxable={} paye={} uif={} sdl={}",
                employee.getId(), rules.taxYear(), r.taxableIncome(), r.payeAmount(), r.uifEmployee(), r.sdlAmount());

        return new PayrollResult(
                r.grossSalary(), r.travelAllowance(), r.medicalAid(), r.pension(),
                r.payeAmount(), r.uifEmployee(), r.uifEmployer(), r.sdlAmount(),
                r.taxableIncome(), r.taxYear());
    }
}
