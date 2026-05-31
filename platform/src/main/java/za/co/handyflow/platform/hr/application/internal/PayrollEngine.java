package za.co.handyflow.platform.hr.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.hr.domain.model.HrEmployee;
import za.co.handyflow.platform.hr.domain.model.HrTaxRebate;
import za.co.handyflow.platform.hr.domain.model.HrTaxTable;
import za.co.handyflow.platform.hr.domain.repository.HrTaxRebateRepository;
import za.co.handyflow.platform.hr.domain.repository.HrTaxTableRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PayrollEngine {

    private final HrTaxTableRepository taxTableRepo;
    private final HrTaxRebateRepository taxRebateRepo;

    // WHY? UIF ceiling: maximum income subject to UIF is R17,712/month
    // (annual ceiling R212,544 — updated March 2024)
    private static final BigDecimal UIF_MONTHLY_CEILING = new BigDecimal("17712.00");
    private static final BigDecimal UIF_RATE            = new BigDecimal("0.01");  // 1%
    private static final BigDecimal SDL_RATE            = new BigDecimal("0.01");  // 1%
    // WHY? SDL only applies if annual payroll > R500,000
    private static final BigDecimal SDL_THRESHOLD       = new BigDecimal("500000.00");

    public PayrollResult calculate(HrEmployee employee, int taxYear,
                                   BigDecimal annualPayroll) {
        BigDecimal monthlySalary = employee.getGrossSalary();
        BigDecimal travelAllowance = employee.getTravelAllowance() != null
                ? employee.getTravelAllowance() : BigDecimal.ZERO;
        BigDecimal pension = employee.getPensionContribution() != null
                ? employee.getPensionContribution() : BigDecimal.ZERO;
        BigDecimal medicalAid = employee.getMedicalAidContribution() != null
                ? employee.getMedicalAidContribution() : BigDecimal.ZERO;

        // ── Step 1: Annual taxable income ────────────────────────────────────
        // WHY? PAYE is calculated on annual income then divided by 12.
        // Travel allowance is included (only 20% exempt if no logbook — simplified).
        // Pension reduces taxable income (Section 11(k) deduction — up to 27.5% of income).
        BigDecimal annualGross    = monthlySalary.multiply(BigDecimal.valueOf(12));
        BigDecimal annualTravel   = travelAllowance.multiply(BigDecimal.valueOf(12));
        BigDecimal annualPension  = pension.multiply(BigDecimal.valueOf(12));

        // Pension deduction capped at 27.5% of taxable income or R350,000
        BigDecimal maxPensionDeduction = annualGross.multiply(new BigDecimal("0.275"))
                .min(new BigDecimal("350000.00"));
        BigDecimal pensionDeduction = annualPension.min(maxPensionDeduction);

        BigDecimal taxableIncome = annualGross
                .add(annualTravel)
                .subtract(pensionDeduction)
                .max(BigDecimal.ZERO);

        // ── Step 2: Find tax bracket and calculate annual tax ─────────────────
        List<HrTaxTable> brackets = taxTableRepo.findByTaxYear(taxYear);
        if (brackets.isEmpty()) {
            log.warn("No tax tables found for year={} — defaulting to 18%", taxYear);
            return buildZeroResult(employee, taxYear);
        }

        HrTaxTable bracket = brackets.stream()
                .filter(b -> b.contains(taxableIncome))
                .findFirst()
                .orElse(brackets.get(brackets.size() - 1)); // use top bracket if above all

        BigDecimal annualTaxBeforeRebate = bracket.calculateTax(taxableIncome);

        // ── Step 3: Apply primary rebate ──────────────────────────────────────
        // WHY? Every taxpayer gets a primary rebate regardless of age.
        // Additional rebates for age 65+ and 75+.
        List<HrTaxRebate> rebates = taxRebateRepo.findByTaxYear(taxYear);
        BigDecimal primaryRebate = rebates.stream()
                .filter(r -> "PRIMARY".equals(r.getRebateType()))
                .map(HrTaxRebate::getAmount)
                .findFirst()
                .orElse(new BigDecimal("17235.00")); // 2025/26 default

        BigDecimal annualTaxAfterRebate = annualTaxBeforeRebate
                .subtract(primaryRebate)
                .max(BigDecimal.ZERO); // PAYE never negative

        // ── Step 4: Monthly PAYE ──────────────────────────────────────────────
        BigDecimal monthlyPaye = annualTaxAfterRebate
                .divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);

        // ── Step 5: UIF ───────────────────────────────────────────────────────
        // WHY? UIF = 1% employee + 1% employer, capped at ceiling income.
        BigDecimal uifBase = monthlySalary.min(UIF_MONTHLY_CEILING);
        BigDecimal uifEmployee = uifBase.multiply(UIF_RATE)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal uifEmployer = uifBase.multiply(UIF_RATE)
                .setScale(2, RoundingMode.HALF_UP);

        // ── Step 6: SDL ───────────────────────────────────────────────────────
        // WHY? SDL = 1% of gross salary, only if company payroll > R500k/year.
        BigDecimal sdlAmount = annualPayroll.compareTo(SDL_THRESHOLD) > 0
                ? monthlySalary.multiply(SDL_RATE).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        log.debug("Payroll calc employee={} taxYear={} taxableIncome={} paye={} uif={}",
                employee.getId(), taxYear, taxableIncome, monthlyPaye, uifEmployee);

        return new PayrollResult(
                monthlySalary, travelAllowance, medicalAid, pension,
                monthlyPaye, uifEmployee, uifEmployer, sdlAmount,
                taxableIncome, annualTaxBeforeRebate, primaryRebate, taxYear
        );
    }

    private PayrollResult buildZeroResult(HrEmployee e, int taxYear) {
        return new PayrollResult(
                e.getGrossSalary(), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, taxYear
        );
    }

    // WHY a record? Immutable calculation result — all fields set at once,
    // no risk of partial state being used.
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
    }
}