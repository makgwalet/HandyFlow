package za.co.handyflow.platform.payrollbureau.application.internal;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.payrollbureau.domain.model.PayEmployee;
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

    public PayrollResult calculate(PayEmployee employee, StatutoryYearRules rules, BigDecimal annualPayroll) {
        BigDecimal medical = employee.getMedicalAidContribution() == null ? BigDecimal.ZERO : employee.getMedicalAidContribution();
        // The bureau does not record medical scheme dependants yet, so the taxpayer's own credit is all it can give.
        int medicalMembers = medical.signum() > 0 ? 1 : 0;
        SarsPayrollResult r = SarsPayeCalculator.calculate(new SarsPayeCalculator.Inputs(
                employee.getGrossSalary(), employee.getTravelAllowance(), employee.getPensionContribution(),
                employee.getMedicalAidContribution(), employee.getDateOfBirth(), medicalMembers, annualPayroll), rules);

        log.debug("Payroll bureau employee={} year={} taxable={} paye={} uif={} sdl={}",
                employee.getId(), rules.taxYear(), r.taxableIncome(), r.payeAmount(), r.uifEmployee(), r.sdlAmount());

        return new PayrollResult(
                r.grossSalary(), r.travelAllowance(), r.medicalAid(), r.pension(),
                r.payeAmount(), r.uifEmployee(), r.uifEmployer(), r.sdlAmount(),
                r.taxableIncome(), r.taxYear());
    }
}
