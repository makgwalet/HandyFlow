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
                         BigDecimal annualPayroll,
                         BigDecimal employerMedical, BigDecimal employerRetirement, BigDecimal otherFringe,
                         BigDecimal irregular) {
        /** Without a bonus or back pay, as before they existed. */
        public Inputs(BigDecimal monthlySalary, BigDecimal travelAllowance, BigDecimal pensionContribution,
                      BigDecimal medicalAidContribution, LocalDate dateOfBirth, int medicalMembers, BigDecimal annualPayroll,
                      BigDecimal employerMedical, BigDecimal employerRetirement, BigDecimal otherFringe) {
            this(monthlySalary, travelAllowance, pensionContribution, medicalAidContribution, dateOfBirth, medicalMembers, annualPayroll,
                    employerMedical, employerRetirement, otherFringe, BigDecimal.ZERO);
        }

        /** Without employer benefits, as before they existed. */
        public Inputs(BigDecimal monthlySalary, BigDecimal travelAllowance, BigDecimal pensionContribution,
                      BigDecimal medicalAidContribution, LocalDate dateOfBirth, int medicalMembers, BigDecimal annualPayroll) {
            this(monthlySalary, travelAllowance, pensionContribution, medicalAidContribution, dateOfBirth, medicalMembers, annualPayroll,
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }
    }

    public static SarsPayrollResult calculate(Inputs in, StatutoryYearRules r) {
        return calculate(in, r, 12, null);
    }

    /**
     * Periodic method for a payroll paid {@code periodsPerYear} times a year (12 monthly, 26 fortnightly, 52 weekly), the way SARS's
     * weekly and fortnightly deduction tables work: the period's pay is annualised by the number of periods, taxed as a year, and a
     * period's share taken. In this overload {@code in.monthlySalary()} and the other pay amounts are for the period, not the month.
     * The medical credit and rebates are annual figures and are shared out the same way.
     * <p>
     * UIF is a monthly ceiling, so for a payroll that is not monthly the base already counted this calendar month is passed in
     * ({@code uifBaseAlreadyThisMonth}) and only the room left under the ceiling is used. It is ignored when {@code periodsPerYear} is 12.
     */
    public static SarsPayrollResult calculate(Inputs in, StatutoryYearRules r, int periodsPerYear, BigDecimal uifBaseAlreadyThisMonth) {
        if (periodsPerYear < 1) throw new IllegalArgumentException("periodsPerYear must be at least 1");
        final BigDecimal periods = BigDecimal.valueOf(periodsPerYear);
        BigDecimal salary = in.monthlySalary();
        BigDecimal travel = nvl(in.travelAllowance());
        BigDecimal pension = nvl(in.pensionContribution());
        BigDecimal medicalAid = nvl(in.medicalAidContribution());

        BigDecimal employerMedical = nvl(in.employerMedical());
        BigDecimal employerRetirement = nvl(in.employerRetirement());
        BigDecimal otherFringe = nvl(in.otherFringe());

        BigDecimal annualGross = salary.multiply(periods);
        BigDecimal annualTravel = travel.multiply(r.travelTaxablePct()).multiply(periods);
        // Employer contributions to a medical scheme or a retirement fund, and other fringe benefits, are taxable benefits: they add to
        // the income tax is worked out on. What the employer pays into a retirement fund counts as the employee's own contribution for
        // the deduction, so it shares the cap with the employee's contribution.
        BigDecimal annualFringe = employerMedical.add(employerRetirement).add(otherFringe).multiply(periods);
        BigDecimal annualPension = pension.add(employerRetirement).multiply(periods);
        BigDecimal remuneration = annualGross.add(annualTravel).add(annualFringe);

        // Age is taken at the last day of the tax year: someone who turns 65 during the year gets the rebate for the whole year.
        int age = in.dateOfBirth() == null ? 0 : Period.between(in.dateOfBirth(), r.effectiveTo()).getYears();
        BigDecimal primary = r.primaryRebate();
        BigDecimal secondary = age >= 65 ? r.secondaryRebate() : BigDecimal.ZERO;
        BigDecimal tertiary = age >= 75 ? r.tertiaryRebate() : BigDecimal.ZERO;
        BigDecimal annualCredit = r.monthlyMedicalCredit(in.medicalMembers()).multiply(TWELVE);

        // The month's regular pay, annualised: what the periodic method has always done.
        BigDecimal taxable = taxableOf(remuneration, annualPension, r);
        BigDecimal taxBefore = tax(taxable, r);
        BigDecimal annualPaye = annualPayeOf(taxBefore, primary, secondary, tertiary, annualCredit);
        BigDecimal paye = annualPaye.divide(periods, 2, RoundingMode.HALF_UP);

        // A bonus or back pay is not annualised. The tax on it is the extra tax the year bears once it is added to the annualised
        // regular pay (the method SARS prescribes for annual payments), and all of it falls in the month it is paid.
        BigDecimal irregular = nvl(in.irregular());
        if (irregular.signum() > 0) {
            BigDecimal taxableWith = taxableOf(remuneration.add(irregular), annualPension, r);
            BigDecimal taxBeforeWith = tax(taxableWith, r);
            BigDecimal annualPayeWith = annualPayeOf(taxBeforeWith, primary, secondary, tertiary, annualCredit);
            paye = paye.add(annualPayeWith.subtract(annualPaye).setScale(2, RoundingMode.HALF_UP));
            taxable = taxableWith;
            taxBefore = taxBeforeWith;
        }

        // A bonus or back pay is remuneration for UIF and SDL in the month it is paid.
        BigDecimal subject = salary.add(irregular);
        BigDecimal uifBase = periodsPerYear == 12 ? subject.min(r.uifMonthlyCeiling()) : uifBase(subject, r.uifMonthlyCeiling(), uifBaseAlreadyThisMonth);
        BigDecimal uifEmployee = uifBase.multiply(r.uifRateEmployee()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal uifEmployer = uifBase.multiply(r.uifRateEmployer()).setScale(2, RoundingMode.HALF_UP);

        BigDecimal sdl = in.annualPayroll().compareTo(r.sdlThreshold()) > 0
                ? subject.multiply(r.sdlRate()).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        return new SarsPayrollResult(salary, travel, medicalAid, pension, paye, uifEmployee, uifEmployer, sdl,
                taxable, taxBefore, primary, secondary, tertiary, annualCredit, r.taxYear());
    }

    /** The part of {@code remuneration} that is liable for UIF this period: what is left under the monthly ceiling after what was already counted this month. */
    public static BigDecimal uifBase(BigDecimal remuneration, BigDecimal monthlyCeiling, BigDecimal alreadyThisMonth) {
        BigDecimal room = monthlyCeiling.subtract(nvl(alreadyThisMonth)).max(BigDecimal.ZERO);
        return remuneration.min(room);
    }

    /** The income tax is worked out on: the remuneration less the retirement deduction, which is capped at a share of the remuneration. */
    private static BigDecimal taxableOf(BigDecimal remuneration, BigDecimal annualPension, StatutoryYearRules r) {
        BigDecimal cap = remuneration.multiply(r.retirementCapPct()).min(r.retirementCapAmount());
        return remuneration.subtract(annualPension.min(cap)).max(BigDecimal.ZERO);
    }

    /** The year's tax after the rebates and the medical credit; never below nil. */
    private static BigDecimal annualPayeOf(BigDecimal taxBefore, BigDecimal primary, BigDecimal secondary, BigDecimal tertiary, BigDecimal credit) {
        return taxBefore.subtract(primary).subtract(secondary).subtract(tertiary).max(BigDecimal.ZERO).subtract(credit).max(BigDecimal.ZERO);
    }

    /** The year's PAYE on an annual taxable income, after the given rebates and medical credit; for the averaging method. */
    public static BigDecimal annualPaye(BigDecimal annualTaxable, BigDecimal primary, BigDecimal secondary, BigDecimal tertiary,
                                        BigDecimal annualCredit, StatutoryYearRules r) {
        return annualPayeOf(tax(annualTaxable, r), primary, secondary, tertiary, annualCredit);
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
