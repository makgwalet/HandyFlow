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
import java.time.LocalDate;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PayrollEngine {

    private final HrTaxTableRepository  taxTableRepo;
    private final HrTaxRebateRepository taxRebateRepo;

    // UIF ceiling: R17,712/month (updated March 2024, Act 63 of 2001)
    private static final BigDecimal UIF_MONTHLY_CEILING = new BigDecimal("17712.00");
    private static final BigDecimal UIF_RATE            = new BigDecimal("0.01");  // 1%
    private static final BigDecimal SDL_RATE            = new BigDecimal("0.01");  // 1%
    private static final BigDecimal SDL_THRESHOLD       = new BigDecimal("500000.00");

    // FIX 3.4: SARS binding: only 20% of travel allowance is exempt from tax
    // where no accurate records are kept (80% is taxable — ITA s.8(1)(b))
    private static final BigDecimal TRAVEL_TAXABLE_FRACTION = new BigDecimal("0.80");

    // FIX 3.6: Medical Tax Credit rates 2025/26 — not a deduction but a credit
    // R364 per month for the taxpayer, R364 for first dependant, R246 for each additional
    private static final BigDecimal MTC_PRIMARY     = new BigDecimal("364.00");
    private static final BigDecimal MTC_FIRST_DEP   = new BigDecimal("364.00");
    private static final BigDecimal MTC_ADDITIONAL  = new BigDecimal("246.00");

    public PayrollResult calculate(HrEmployee employee, int taxYear,
                                   BigDecimal annualPayroll) {
        BigDecimal monthlySalary   = employee.getGrossSalary();
        BigDecimal travelAllowance = nvl(employee.getTravelAllowance());
        BigDecimal pension         = nvl(employee.getPensionContribution());
        BigDecimal medicalAid      = nvl(employee.getMedicalAidContribution());

        // ── Step 1: Annual taxable income ─────────────────────────────────────
        BigDecimal annualGross   = monthlySalary.multiply(BigDecimal.valueOf(12));
        // FIX: 80% of travel allowance is taxable, not 100%
        BigDecimal annualTravel  = travelAllowance
                .multiply(TRAVEL_TAXABLE_FRACTION)
                .multiply(BigDecimal.valueOf(12));
        BigDecimal annualPension = pension.multiply(BigDecimal.valueOf(12));

        // Pension deduction: Section 11(k) — capped at 27.5% of income or R350,000
        BigDecimal maxPensionDeduction = annualGross
                .add(annualTravel)
                .multiply(new BigDecimal("0.275"))
                .min(new BigDecimal("350000.00"));
        BigDecimal pensionDeduction = annualPension.min(maxPensionDeduction);

        BigDecimal taxableIncome = annualGross
                .add(annualTravel)
                .subtract(pensionDeduction)
                .max(BigDecimal.ZERO);

        // ── Step 2: Tax bracket lookup ────────────────────────────────────────
        List<HrTaxTable> brackets = taxTableRepo.findByTaxYear(taxYear);
        if (brackets.isEmpty()) {
            log.warn("No tax tables found for year={} — zero PAYE applied", taxYear);
            return buildZeroResult(employee, taxYear);
        }

        HrTaxTable bracket = brackets.stream()
                .filter(b -> b.contains(taxableIncome))
                .findFirst()
                .orElse(brackets.get(brackets.size() - 1));

        BigDecimal annualTaxBeforeRebates = bracket.calculateTax(taxableIncome);

        // ── Step 3: Primary rebate (all taxpayers) ────────────────────────────
        List<HrTaxRebate> rebates = taxRebateRepo.findByTaxYear(taxYear);

        BigDecimal primaryRebate = rebates.stream()
                .filter(r -> "PRIMARY".equals(r.getRebateType()))
                .map(HrTaxRebate::getAmount)
                .findFirst()
                .orElse(new BigDecimal("17235.00")); // 2025/26 default

        // FIX 3.5: Secondary rebate (age 65+) and tertiary rebate (age 75+)
        BigDecimal secondaryRebate  = BigDecimal.ZERO;
        BigDecimal tertiaryRebate   = BigDecimal.ZERO;
        if (employee.getDateOfBirth() != null) {
            int age = LocalDate.now().getYear() - employee.getDateOfBirth().getYear();
            if (age >= 65) {
                secondaryRebate = rebates.stream()
                        .filter(r -> "SECONDARY".equals(r.getRebateType()))
                        .map(HrTaxRebate::getAmount)
                        .findFirst()
                        .orElse(new BigDecimal("9444.00")); // 2025/26 default
            }
            if (age >= 75) {
                tertiaryRebate = rebates.stream()
                        .filter(r -> "TERTIARY".equals(r.getRebateType()))
                        .map(HrTaxRebate::getAmount)
                        .findFirst()
                        .orElse(new BigDecimal("3145.00")); // 2025/26 default
            }
        }

        BigDecimal totalRebates = primaryRebate.add(secondaryRebate).add(tertiaryRebate);
        BigDecimal annualTaxAfterRebates = annualTaxBeforeRebates
                .subtract(totalRebates)
                .max(BigDecimal.ZERO);

        // FIX 3.6: Medical Tax Credit — CREDIT against tax, not deduction from income
        // MTC = R364/month for taxpayer + R364 first dependant + R246 each additional
        // Simplified: assume taxpayer only (no dependant data captured yet)
        BigDecimal annualMtc = MTC_PRIMARY.multiply(BigDecimal.valueOf(12));
        BigDecimal annualTaxAfterMtc = annualTaxAfterRebates
                .subtract(annualMtc)
                .max(BigDecimal.ZERO);

        // ── Step 4: Monthly PAYE ──────────────────────────────────────────────
        BigDecimal monthlyPaye = annualTaxAfterMtc
                .divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);

        // ── Step 5: UIF ───────────────────────────────────────────────────────
        BigDecimal uifBase     = monthlySalary.min(UIF_MONTHLY_CEILING);
        BigDecimal uifEmployee = uifBase.multiply(UIF_RATE).setScale(2, RoundingMode.HALF_UP);
        BigDecimal uifEmployer = uifBase.multiply(UIF_RATE).setScale(2, RoundingMode.HALF_UP);

        // ── Step 6: SDL ───────────────────────────────────────────────────────
        BigDecimal sdlAmount = annualPayroll.compareTo(SDL_THRESHOLD) > 0
                ? monthlySalary.multiply(SDL_RATE).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        log.debug("Payroll employee={} year={} taxable={} paye={} uif={} sdl={}",
                employee.getId(), taxYear, taxableIncome, monthlyPaye, uifEmployee, sdlAmount);

        return new PayrollResult(
                monthlySalary, travelAllowance, medicalAid, pension,
                monthlyPaye, uifEmployee, uifEmployer, sdlAmount,
                taxableIncome, annualTaxBeforeRebates, primaryRebate,
                secondaryRebate, tertiaryRebate, annualMtc, taxYear
        );
    }

    private static BigDecimal nvl(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private PayrollResult buildZeroResult(HrEmployee e, int taxYear) {
        return new PayrollResult(
                e.getGrossSalary(), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, taxYear
        );
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
            BigDecimal secondaryRebate,   // NEW — age 65+
            BigDecimal tertiaryRebate,    // NEW — age 75+
            BigDecimal medicalTaxCredit,  // NEW — replaces deduction approach
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
