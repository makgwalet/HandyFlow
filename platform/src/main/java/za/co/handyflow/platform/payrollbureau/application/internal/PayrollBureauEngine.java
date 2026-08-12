package za.co.handyflow.platform.payrollbureau.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.payrollbureau.domain.model.PayEmployee;
import za.co.handyflow.platform.shared.SarsTaxRebate;
import za.co.handyflow.platform.shared.SarsTaxRebateRepository;
import za.co.handyflow.platform.shared.SarsTaxTable;
import za.co.handyflow.platform.shared.SarsTaxTableRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/**
 * PAYE/UIF/SDL calculation for the payroll bureau — formula-for-formula
 * mirror of hr.application.internal.PayrollEngine, deliberately NOT
 * importing that class directly (hr is architecturally isolated by
 * design — nothing else in this codebase is allowed to depend on it,
 * see HandyFlow BOS Discovery doc Section 4 Finding 1 — importing it
 * here would recreate the exact Recruiter->HR violation already found
 * and fixed earlier in this engagement).
 * <p>
 * WHAT IS SHARED, DELIBERATELY: the tax bracket/rebate DATA
 * (SarsTaxTable/SarsTaxRebate, now in `shared` — see that move's own
 * migration notes) — because tax brackets are national reference data,
 * not HR business logic, and duplicating them would create real
 * compliance drift risk. What is NOT shared: the calculation CODE
 * itself, which stays a separate, independently-readable class here —
 * same "duplication of logic costs less than coupling to an isolated
 * module" tradeoff already used elsewhere in this codebase
 * (RecruiterPdfGenerator, SecurityGuardPayStatementPdfService).
 * <p>
 * IF THIS FORMULA EVER NEEDS TO CHANGE: check hr.PayrollEngine too —
 * these are two independent implementations of the same law, and a
 * SARS rule change (e.g. a new travel-allowance taxable fraction) needs
 * updating in both places. This is the accepted cost of the isolation
 * boundary; flagging it explicitly rather than leaving it as a silent trap.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PayrollBureauEngine {

    private final SarsTaxTableRepository taxTableRepo;
    private final SarsTaxRebateRepository taxRebateRepo;

    private static final BigDecimal UIF_MONTHLY_CEILING = new BigDecimal("17712.00");
    private static final BigDecimal UIF_RATE = new BigDecimal("0.01");
    private static final BigDecimal SDL_RATE = new BigDecimal("0.01");
    private static final BigDecimal SDL_THRESHOLD = new BigDecimal("500000.00");
    private static final BigDecimal TRAVEL_TAXABLE_FRACTION = new BigDecimal("0.80");
    private static final BigDecimal MTC_PRIMARY = new BigDecimal("364.00");

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
        BigDecimal monthlySalary = employee.getGrossSalary();
        BigDecimal travelAllowance = nvl(employee.getTravelAllowance());
        BigDecimal pension = nvl(employee.getPensionContribution());
        BigDecimal medicalAid = nvl(employee.getMedicalAidContribution());

        // Step 1: annual taxable income
        BigDecimal annualGross = monthlySalary.multiply(BigDecimal.valueOf(12));
        BigDecimal annualTravel = travelAllowance.multiply(TRAVEL_TAXABLE_FRACTION).multiply(BigDecimal.valueOf(12));
        BigDecimal annualPension = pension.multiply(BigDecimal.valueOf(12));

        BigDecimal maxPensionDeduction = annualGross.add(annualTravel)
                .multiply(new BigDecimal("0.275")).min(new BigDecimal("350000.00"));
        BigDecimal pensionDeduction = annualPension.min(maxPensionDeduction);

        BigDecimal taxableIncome = annualGross.add(annualTravel).subtract(pensionDeduction).max(BigDecimal.ZERO);

        // Step 2: tax bracket lookup
        List<SarsTaxTable> brackets = taxTableRepo.findByTaxYear(taxYear);
        if (brackets.isEmpty()) {
            log.warn("No SARS tax tables found for year={} — zero PAYE applied", taxYear);
            return new PayrollResult(monthlySalary, travelAllowance, medicalAid, pension,
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, taxableIncome, taxYear);
        }
        SarsTaxTable bracket = brackets.stream()
                .filter(b -> b.contains(taxableIncome))
                .findFirst()
                .orElse(brackets.get(brackets.size() - 1));
        BigDecimal annualTaxBeforeRebates = bracket.calculateTax(taxableIncome);

        // Step 3: rebates
        List<SarsTaxRebate> rebates = taxRebateRepo.findByTaxYear(taxYear);
        BigDecimal primaryRebate = rebates.stream()
                .filter(r -> "PRIMARY".equals(r.getRebateType())).map(SarsTaxRebate::getAmount)
                .findFirst().orElse(new BigDecimal("17235.00"));
        BigDecimal secondaryRebate = BigDecimal.ZERO;
        BigDecimal tertiaryRebate = BigDecimal.ZERO;
        if (employee.getDateOfBirth() != null) {
            int age = LocalDate.now().getYear() - employee.getDateOfBirth().getYear();
            if (age >= 65) {
                secondaryRebate = rebates.stream()
                        .filter(r -> "SECONDARY".equals(r.getRebateType())).map(SarsTaxRebate::getAmount)
                        .findFirst().orElse(new BigDecimal("9444.00"));
            }
            if (age >= 75) {
                tertiaryRebate = rebates.stream()
                        .filter(r -> "TERTIARY".equals(r.getRebateType())).map(SarsTaxRebate::getAmount)
                        .findFirst().orElse(new BigDecimal("3145.00"));
            }
        }
        BigDecimal totalRebates = primaryRebate.add(secondaryRebate).add(tertiaryRebate);
        BigDecimal annualTaxAfterRebates = annualTaxBeforeRebates.subtract(totalRebates).max(BigDecimal.ZERO);

        // Medical tax credit (taxpayer only — no dependant data captured, same simplification as hr.PayrollEngine)
        BigDecimal annualMtc = MTC_PRIMARY.multiply(BigDecimal.valueOf(12));
        BigDecimal annualTaxAfterMtc = annualTaxAfterRebates.subtract(annualMtc).max(BigDecimal.ZERO);

        // Step 4: monthly PAYE
        BigDecimal monthlyPaye = annualTaxAfterMtc.divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);

        // Step 5: UIF
        BigDecimal uifBase = monthlySalary.min(UIF_MONTHLY_CEILING);
        BigDecimal uifEmployee = uifBase.multiply(UIF_RATE).setScale(2, RoundingMode.HALF_UP);
        BigDecimal uifEmployer = uifBase.multiply(UIF_RATE).setScale(2, RoundingMode.HALF_UP);

        // Step 6: SDL
        BigDecimal sdlAmount = annualPayroll.compareTo(SDL_THRESHOLD) > 0
                ? monthlySalary.multiply(SDL_RATE).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        log.debug("Payroll bureau employee={} year={} taxable={} paye={} uif={} sdl={}",
                employee.getId(), taxYear, taxableIncome, monthlyPaye, uifEmployee, sdlAmount);

        return new PayrollResult(monthlySalary, travelAllowance, medicalAid, pension,
                monthlyPaye, uifEmployee, uifEmployer, sdlAmount, taxableIncome, taxYear);
    }

    private static BigDecimal nvl(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}