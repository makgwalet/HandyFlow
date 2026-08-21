package za.co.handyflow.platform.hr.application.internal;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.hr.domain.model.HrEmployee;
import za.co.handyflow.platform.shared.SarsPayrollCalculator;
import za.co.handyflow.platform.shared.SarsPayrollResult;
import za.co.handyflow.platform.shared.SarsTaxRebateRepository;
import za.co.handyflow.platform.shared.SarsTaxTableRepository;

import java.math.BigDecimal;

/**
 * FIX: backlog 1.4 — "Duplicate PAYE/UIF/SDL statutory payroll calculation."
 * <p>
 * Was an independent PAYE/UIF/SDL implementation, confirmed
 * formula-for-formula identical to payrollbureau.PayrollBureauEngine's own
 * (see PayrollEngineParityTest, which already locked that in as a
 * regression guard before this change). Now a thin adapter over
 * {@link SarsPayrollCalculator} — see that class's own Javadoc for the
 * full extraction rationale and the one genuine behaviour discrepancy
 * found and resolved during the consolidation.
 * <p>
 * PUBLIC CONSTRUCTOR DELIBERATELY UNCHANGED — still takes the two repos
 * directly, exactly as before, rather than a SarsPayrollCalculator.
 * PayrollEngineParityTest constructs this class directly with
 * {@code new PayrollEngine(taxTableRepo, taxRebateRepo)}; preserving that
 * exact signature means this refactor requires zero test changes, and
 * that same parity test now additionally proves the extraction didn't
 * alter behaviour — both engines still produce identical output, because
 * both are now backed by the same calculator.
 */
@Slf4j
@Component
public class PayrollEngine {

    private final SarsPayrollCalculator calculator;

    public PayrollEngine(SarsTaxTableRepository taxTableRepo, SarsTaxRebateRepository taxRebateRepo) {
        this.calculator = new SarsPayrollCalculator(taxTableRepo, taxRebateRepo);
    }

    public PayrollResult calculate(HrEmployee employee, int taxYear, BigDecimal annualPayroll) {
        SarsPayrollResult r = calculator.calculate(
                employee.getGrossSalary(),
                employee.getTravelAllowance(),
                employee.getPensionContribution(),
                employee.getMedicalAidContribution(),
                employee.getDateOfBirth(),
                taxYear,
                annualPayroll
        );

        log.debug("Payroll employee={} year={} taxable={} paye={} uif={} sdl={}",
                employee.getId(), taxYear, r.taxableIncome(), r.payeAmount(), r.uifEmployee(), r.sdlAmount());

        return new PayrollResult(
                r.grossSalary(), r.travelAllowance(), r.medicalAid(), r.pension(),
                r.payeAmount(), r.uifEmployee(), r.uifEmployer(), r.sdlAmount(),
                r.taxableIncome(), r.taxBeforeRebate(), r.primaryRebate(),
                r.secondaryRebate(), r.tertiaryRebate(), r.medicalTaxCredit(), r.taxYear()
        );
    }

    /**
     * UNCHANGED, field-for-field and method-for-method, from the
     * pre-extraction version. Kept as hr's own local record (rather than
     * replaced by SarsPayrollResult directly) purely so
     * PayrollService/PayslipPdfGenerator — the real callers of
     * {@code calculate()} — need zero changes: they still reference
     * {@code PayrollEngine.PayrollResult} exactly as before.
     */
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
            int taxYear
    ) {
        public BigDecimal totalEarnings() {
            return grossSalary.add(travelAllowance);
        }

        public BigDecimal totalDeductions() {
            // MTC is a credit against tax, already baked into payeAmount
            return payeAmount.add(uifEmployee).add(medicalAid).add(pension);
        }

        public BigDecimal netPay() {
            return totalEarnings().subtract(totalDeductions());
        }

        public BigDecimal totalRebates() {
            return primaryRebate.add(secondaryRebate).add(tertiaryRebate);
        }
    }
}