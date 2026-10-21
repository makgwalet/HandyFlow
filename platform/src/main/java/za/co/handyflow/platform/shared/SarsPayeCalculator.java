package za.co.handyflow.platform.shared;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Period;

/**
 * Monthly PAYE, UIF and SDL for a regular monthly salary under one tax year's approved rules.
 * <p>
 * Pure: no database, no clock. The rules are passed in, so the same inputs always give the same answer, and an old
 * period can be recalculated exactly. Not a general PAYE engine: it treats the month on its own (no year-to-date
 * method), has no bonus, overtime or back pay, and no pro-rata. Those are later slices.
 */
public final class SarsPayeCalculator {

    private static final BigDecimal TWELVE = BigDecimal.valueOf(12);

    private SarsPayeCalculator() {}

    /**
     * @param medicalMembers people covered by a medical scheme, the employee included (0 = no credit)
     * @param annualPayroll  the tenant's annual payroll, for the SDL threshold
     */
    public record Inputs(BigDecimal monthlySalary, BigDecimal travelAllowance, BigDecimal pensionContribution,
                         BigDecimal medicalAidContribution, LocalDate dateOfBirth, int medicalMembers,
                         BigDecimal annualPayroll) {}

    public static SarsPayrollResult calculate(Inputs in, StatutoryYearRules r) {
        BigDecimal salary = in.monthlySalary();
        BigDecimal travel = nvl(in.travelAllowance());
        BigDecimal pension = nvl(in.pensionContribution());
        BigDecimal medicalAid = nvl(in.medicalAidContribution());

        BigDecimal annualGross = salary.multiply(TWELVE);
        BigDecimal annualTravel = travel.multiply(r.travelTaxablePct()).multiply(TWELVE);
        BigDecimal annualPension = pension.multiply(TWELVE);

        BigDecimal cap = annualGross.add(annualTravel).multiply(r.retirementCapPct()).min(r.retirementCapAmount());
        BigDecimal pensionDeduction = annualPension.min(cap);
        BigDecimal taxable = annualGross.add(annualTravel).subtract(pensionDeduction).max(BigDecimal.ZERO);

        BigDecimal taxBefore = tax(taxable, r);

        // Age is taken at the last day of the tax year: someone who turns 65 during the year gets the rebate for the whole year.
        int age = in.dateOfBirth() == null ? 0 : Period.between(in.dateOfBirth(), r.effectiveTo()).getYears();
        BigDecimal primary = r.primaryRebate();
        BigDecimal secondary = age >= 65 ? r.secondaryRebate() : BigDecimal.ZERO;
        BigDecimal tertiary = age >= 75 ? r.tertiaryRebate() : BigDecimal.ZERO;

        BigDecimal afterRebates = taxBefore.subtract(primary).subtract(secondary).subtract(tertiary).max(BigDecimal.ZERO);
        BigDecimal annualCredit = r.monthlyMedicalCredit(in.medicalMembers()).multiply(TWELVE);
        BigDecimal paye = afterRebates.subtract(annualCredit).max(BigDecimal.ZERO).divide(TWELVE, 2, RoundingMode.HALF_UP);

        BigDecimal uifBase = salary.min(r.uifMonthlyCeiling());
        BigDecimal uifEmployee = uifBase.multiply(r.uifRateEmployee()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal uifEmployer = uifBase.multiply(r.uifRateEmployer()).setScale(2, RoundingMode.HALF_UP);

        BigDecimal sdl = in.annualPayroll().compareTo(r.sdlThreshold()) > 0
                ? salary.multiply(r.sdlRate()).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        return new SarsPayrollResult(salary, travel, medicalAid, pension, paye, uifEmployee, uifEmployer, sdl,
                taxable, taxBefore, primary, secondary, tertiary, annualCredit, r.taxYear());
    }

    /** Annual tax before rebates: the last line whose floor is below the income (the first line for nil income). */
    static BigDecimal tax(BigDecimal income, StatutoryYearRules r) {
        StatutoryYearRules.Bracket line = r.brackets().get(0);
        for (StatutoryYearRules.Bracket b : r.brackets()) if (b.from().compareTo(income) < 0) line = b;
        BigDecimal above = income.subtract(line.from()).max(BigDecimal.ZERO);
        return line.baseTax().add(above.multiply(line.ratePercent()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
    }

    private static BigDecimal nvl(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }
}
