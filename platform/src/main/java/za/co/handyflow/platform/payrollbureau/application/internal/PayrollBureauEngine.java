package za.co.handyflow.platform.payrollbureau.application.internal;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.payrollbureau.domain.model.PayEmployee;
import za.co.handyflow.platform.shared.SarsPayrollCalculator;
import za.co.handyflow.platform.shared.SarsPayrollResult;
import za.co.handyflow.platform.shared.SarsTaxRebateRepository;
import za.co.handyflow.platform.shared.SarsTaxTableRepository;

import java.math.BigDecimal;

/**
 * FIX: backlog 1.4 — see hr.application.internal.PayrollEngine's own
 * Javadoc for the full extraction rationale; this class is the mirror
 * change on the payrollbureau side.
 * <p>
 * DELIBERATELY STILL IMPORTS NOTHING FROM {@code hr} — the isolation this
 * class's prior revision documented is fully preserved:
 * payrollbureau's own package-info.java {@code allowedDependencies}
 * genuinely excludes {@code hr}, for real business reasons (a bureau's
 * clients aren't HandyFlow tenants themselves; see that file's own
 * Javadoc). This now delegates to {@link SarsPayrollCalculator}, which
 * lives in {@code shared} — already an allowed dependency for both
 * modules, exactly as {@code SarsTaxTable}/{@code SarsTaxRebate} already
 * were before this change. Neither module ends up depending on the
 * other; both depend on {@code shared}, same as before.
 * <p>
 * PUBLIC CONSTRUCTOR DELIBERATELY UNCHANGED — see PayrollEngine's own
 * Javadoc for why (PayrollEngineParityTest constructs this class
 * directly with {@code new PayrollBureauEngine(taxTableRepo, taxRebateRepo)}).
 */
@Slf4j
@Component
public class PayrollBureauEngine {

    private final SarsPayrollCalculator calculator;

    public PayrollBureauEngine(SarsTaxTableRepository taxTableRepo, SarsTaxRebateRepository taxRebateRepo) {
        this.calculator = new SarsPayrollCalculator(taxTableRepo, taxRebateRepo);
    }

    /**
     * UNCHANGED, field-for-field and method-for-method, from the
     * pre-extraction version — the real callers (this bureau module's own
     * payslip PDF generator and service layer) need zero changes.
     */
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

    public PayrollResult calculate(PayEmployee employee, int taxYear, BigDecimal annualPayroll) {
        SarsPayrollResult r = calculator.calculate(
                employee.getGrossSalary(),
                employee.getTravelAllowance(),
                employee.getPensionContribution(),
                employee.getMedicalAidContribution(),
                employee.getDateOfBirth(),
                taxYear,
                annualPayroll
        );

        log.debug("Payroll bureau employee={} year={} taxable={} paye={} uif={} sdl={}",
                employee.getId(), taxYear, r.taxableIncome(), r.payeAmount(), r.uifEmployee(), r.sdlAmount());

        return new PayrollResult(
                r.grossSalary(), r.travelAllowance(), r.medicalAid(), r.pension(),
                r.payeAmount(), r.uifEmployee(), r.uifEmployer(), r.sdlAmount(),
                r.taxableIncome(), r.taxYear()
        );
    }
}