package za.co.handyflow.platform.shared;

import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/**
 * FIX: backlog 1.4 — "Duplicate PAYE/UIF/SDL statutory payroll calculation."
 * <p>
 * The single canonical South African statutory payroll formula, extracted
 * from what were two independent, formula-for-formula-identical
 * implementations: hr.application.internal.PayrollEngine and
 * payrollbureau.application.internal.PayrollBureauEngine. Both classes are
 * now thin adapters that map their own module's employee entity
 * (HrEmployee / PayEmployee) into this calculator's plain-parameter
 * signature and map {@link SarsPayrollResult} back into their own existing
 * result record — see either engine's own Javadoc for why their public
 * constructors were deliberately left unchanged.
 * <p>
 * DELIBERATELY NOT a Spring {@code @Component}: it takes the two tax-data
 * repositories as plain constructor parameters and is instantiated
 * directly by each engine, the same way each engine's own constructor was
 * already shaped before this change — this keeps the extraction a pure
 * internal refactor of the two engines rather than introducing a new bean
 * both modules must additionally wire up.
 * <p>
 * ONE GENUINE BEHAVIOUR DIFFERENCE FOUND AND RESOLVED DURING THIS
 * CONSOLIDATION (flagging explicitly, not silently picking one): when no
 * SARS tax table exists for a year, hr.PayrollEngine's old
 * {@code buildZeroResult()} zeroed EVERY field, including travelAllowance/
 * medicalAid/pension/taxableIncome — meaning a payslip generated in that
 * situation would silently show no medical aid or pension deduction taken
 * at all, which has nothing to do with the missing tax bracket data and is
 * a real (if rare — it only fires when tax tables are missing entirely for
 * a year, an operational setup error) incorrect-net-pay bug.
 * payrollbureau.PayrollBureauEngine's equivalent branch correctly kept
 * those figures real and only zeroed the tax-derived fields (PAYE and the
 * rebate/credit breakdown). This calculator keeps payrollbureau's more
 * correct behaviour — UIF/SDL are also zeroed in this branch, matching
 * both engines' prior behaviour exactly, even though UIF/SDL don't
 * actually depend on tax bracket data; changing that further would be a
 * second, separate behavioural fix beyond what this consolidation item
 * covers, so it's left as-is and simply preserved.
 */
@Slf4j
public class SarsPayrollCalculator {

    private final SarsTaxTableRepository taxTableRepo;
    private final SarsTaxRebateRepository taxRebateRepo;

    public SarsPayrollCalculator(SarsTaxTableRepository taxTableRepo, SarsTaxRebateRepository taxRebateRepo) {
        this.taxTableRepo = taxTableRepo;
        this.taxRebateRepo = taxRebateRepo;
    }

    // UIF ceiling: R17,712/month (updated March 2024, Act 63 of 2001)
    private static final BigDecimal UIF_MONTHLY_CEILING = new BigDecimal("17712.00");
    private static final BigDecimal UIF_RATE            = new BigDecimal("0.01");  // 1%
    private static final BigDecimal SDL_RATE            = new BigDecimal("0.01");  // 1%
    private static final BigDecimal SDL_THRESHOLD       = new BigDecimal("500000.00");

    // Only 20% of travel allowance is exempt from tax where no accurate
    // records are kept (80% is taxable — ITA s.8(1)(b)).
    private static final BigDecimal TRAVEL_TAXABLE_FRACTION = new BigDecimal("0.80");

    // Medical Tax Credit rates 2025/26 — a credit against tax, not a
    // deduction from income. R364/month for the taxpayer, R364 for a first
    // dependant, R246 for each additional. Simplified to taxpayer-only
    // here — no dependant data is captured anywhere upstream of this
    // calculator yet, matching both prior engines' identical simplification.
    private static final BigDecimal MTC_PRIMARY = new BigDecimal("364.00");

    /**
     * @param monthlySalary          required, never null (both HrEmployee and
     *                               PayEmployee enforce this at their own level).
     * @param travelAllowance        nullable — treated as zero.
     * @param pensionContribution    nullable — treated as zero.
     * @param medicalAidContribution nullable — treated as zero.
     * @param dateOfBirth            nullable — no age-based rebate applied if absent.
     * @param annualPayroll          the tenant/client's total annual payroll, for the SDL threshold check.
     */
    public SarsPayrollResult calculate(
            BigDecimal monthlySalary,
            BigDecimal travelAllowance,
            BigDecimal pensionContribution,
            BigDecimal medicalAidContribution,
            LocalDate dateOfBirth,
            int taxYear,
            BigDecimal annualPayroll
    ) {
        BigDecimal travel    = nvl(travelAllowance);
        BigDecimal pension   = nvl(pensionContribution);
        BigDecimal medicalAid = nvl(medicalAidContribution);

        // ── Step 1: Annual taxable income ─────────────────────────────────────
        BigDecimal annualGross   = monthlySalary.multiply(BigDecimal.valueOf(12));
        BigDecimal annualTravel  = travel
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
        List<SarsTaxTable> brackets = taxTableRepo.findByTaxYear(taxYear);
        if (brackets.isEmpty()) {
            log.warn("No SARS tax tables found for year={} — zero PAYE applied", taxYear);
            // See this class's own Javadoc — deliberately keeps
            // travelAllowance/medicalAid/pension/taxableIncome real here,
            // only zeroing the tax-derived fields.
            return new SarsPayrollResult(
                    monthlySalary, travel, medicalAid, pension,
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    taxableIncome, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, taxYear
            );
        }

        SarsTaxTable bracket = brackets.stream()
                .filter(b -> b.contains(taxableIncome))
                .findFirst()
                .orElse(brackets.get(brackets.size() - 1));

        BigDecimal annualTaxBeforeRebates = bracket.calculateTax(taxableIncome);

        // ── Step 3: Rebates — primary (all taxpayers), secondary (65+), tertiary (75+) ──
        List<SarsTaxRebate> rebates = taxRebateRepo.findByTaxYear(taxYear);

        BigDecimal primaryRebate = rebates.stream()
                .filter(r -> "PRIMARY".equals(r.getRebateType()))
                .map(SarsTaxRebate::getAmount)
                .findFirst()
                .orElse(new BigDecimal("17235.00")); // 2025/26 default

        BigDecimal secondaryRebate = BigDecimal.ZERO;
        BigDecimal tertiaryRebate  = BigDecimal.ZERO;
        if (dateOfBirth != null) {
            int age = LocalDate.now().getYear() - dateOfBirth.getYear();
            if (age >= 65) {
                secondaryRebate = rebates.stream()
                        .filter(r -> "SECONDARY".equals(r.getRebateType()))
                        .map(SarsTaxRebate::getAmount)
                        .findFirst()
                        .orElse(new BigDecimal("9444.00")); // 2025/26 default
            }
            if (age >= 75) {
                tertiaryRebate = rebates.stream()
                        .filter(r -> "TERTIARY".equals(r.getRebateType()))
                        .map(SarsTaxRebate::getAmount)
                        .findFirst()
                        .orElse(new BigDecimal("3145.00")); // 2025/26 default
            }
        }

        BigDecimal totalRebates = primaryRebate.add(secondaryRebate).add(tertiaryRebate);
        BigDecimal annualTaxAfterRebates = annualTaxBeforeRebates
                .subtract(totalRebates)
                .max(BigDecimal.ZERO);

        // Medical Tax Credit — CREDIT against tax, not deduction from income
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

        return new SarsPayrollResult(
                monthlySalary, travel, medicalAid, pension,
                monthlyPaye, uifEmployee, uifEmployer, sdlAmount,
                taxableIncome, annualTaxBeforeRebates, primaryRebate,
                secondaryRebate, tertiaryRebate, annualMtc, taxYear
        );
    }

    private static BigDecimal nvl(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}