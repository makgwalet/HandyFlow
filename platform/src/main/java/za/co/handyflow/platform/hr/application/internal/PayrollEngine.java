package za.co.handyflow.platform.hr.application.internal;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.hr.domain.model.HrEmployee;
import za.co.handyflow.platform.hr.domain.model.PayeAveragingRules;
import za.co.handyflow.platform.shared.SarsPayeCalculator;
import za.co.handyflow.platform.shared.SarsPayrollResult;
import za.co.handyflow.platform.shared.StatutoryYearRules;

import java.math.BigDecimal;

/**
 * HR's payroll calculation: a thin adapter over {@link SarsPayeCalculator}, which takes its figures from the approved
 * rules of the tax year it is given. Nothing is hard-coded here, and there is no fallback when the rules are missing:
 * the caller gets them from {@link StatutoryRulesService}, which refuses.
 * <p>
 * This used to share its formula with the payroll bureau engine. It no longer does: the old formula gave the medical
 * tax credit to everyone, mislabelled the tax year and fell back to zero tax. The bureau engine still uses the old
 * shared calculator and needs its own review.
 */
@Slf4j
@Component
public class PayrollEngine {

    public PayrollResult calculate(HrEmployee employee, StatutoryYearRules rules, BigDecimal annualPayroll, int medicalMembers) {
        return calculate(employee, rules, annualPayroll, medicalMembers, BigDecimal.ZERO);
    }

    /**
     * With approved overtime for the month. Overtime is part of the month's remuneration: it is added to the salary the calculator
     * annualises, so PAYE, UIF (up to the ceiling) and SDL all see it, and it is reported separately from the basic salary.
     * <p>
     * Annualising a month that includes overtime taxes it as if it recurred every month. That is the periodic method used for the
     * rest of this engine; any difference is settled when the employee's year is assessed. The averaging method SARS also allows
     * for fluctuating pay is not implemented.
     */
    public PayrollResult calculate(HrEmployee employee, StatutoryYearRules rules, BigDecimal annualPayroll, int medicalMembers, BigDecimal overtime) {
        return calculate(employee, rules, annualPayroll, medicalMembers, overtime,
                new Period(employee.getGrossSalary(), employee.getTravelAllowance(), employee.getPensionContribution(), BigDecimal.ZERO));
    }

    /** What is paid for the period when it is not a full month: the salary, travel allowance and pension for the days paid, and any leave paid out. */
    public record Period(BigDecimal basic, BigDecimal travel, BigDecimal pension, BigDecimal leavePayout,
                         BigDecimal employerMedical, BigDecimal employerRetirement, BigDecimal otherFringe,
                         BigDecimal bonus, BigDecimal backPay, BigDecimal noticePay) {
        /** Without pay in place of notice. */
        public Period(BigDecimal basic, BigDecimal travel, BigDecimal pension, BigDecimal leavePayout,
                      BigDecimal employerMedical, BigDecimal employerRetirement, BigDecimal otherFringe,
                      BigDecimal bonus, BigDecimal backPay) {
            this(basic, travel, pension, leavePayout, employerMedical, employerRetirement, otherFringe, bonus, backPay, BigDecimal.ZERO);
        }

        /** Without a bonus or back pay. */
        public Period(BigDecimal basic, BigDecimal travel, BigDecimal pension, BigDecimal leavePayout,
                      BigDecimal employerMedical, BigDecimal employerRetirement, BigDecimal otherFringe) {
            this(basic, travel, pension, leavePayout, employerMedical, employerRetirement, otherFringe, BigDecimal.ZERO, BigDecimal.ZERO);
        }

        public Period(BigDecimal basic, BigDecimal travel, BigDecimal pension, BigDecimal leavePayout) {
            this(basic, travel, pension, leavePayout, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }
    }

    /**
     * For a part period, or a last pay that includes annual leave paid out. The basic salary is what is payable for the days paid; the leave
     * payout is remuneration like the salary, so PAYE, UIF and SDL see it, and it is reported separately. The periodic method annualises the
     * month, so a payout in the last month is taxed as if it recurred and a part month as if the person were paid that little all year;
     * the difference is settled when the employee's year is assessed.
     */
    public PayrollResult calculate(HrEmployee employee, StatutoryYearRules rules, BigDecimal annualPayroll, int medicalMembers, BigDecimal overtime, Period period) {
        BigDecimal ot = overtime == null ? BigDecimal.ZERO : overtime;
        BigDecimal payout = period.leavePayout() == null ? BigDecimal.ZERO : period.leavePayout();
        BigDecimal bonus = period.bonus() == null ? BigDecimal.ZERO : period.bonus();
        BigDecimal backPay = period.backPay() == null ? BigDecimal.ZERO : period.backPay();
        BigDecimal noticePay = period.noticePay() == null ? BigDecimal.ZERO : period.noticePay();
        // A bonus and back pay are not part of the month that is annualised: they are taxed on top of it as annual payments.
        SarsPayrollResult r = SarsPayeCalculator.calculate(new SarsPayeCalculator.Inputs(
                period.basic().add(ot).add(payout), period.travel(), period.pension(),
                employee.getMedicalAidContribution(), employee.getDateOfBirth(), medicalMembers, annualPayroll,
                period.employerMedical(), period.employerRetirement(), period.otherFringe(), bonus.add(backPay).add(noticePay)), rules);

        log.debug("Payroll employee={} year={} taxable={} paye={} uif={} sdl={} medicalMembers={}",
                employee.getId(), rules.taxYear(), r.taxableIncome(), r.payeAmount(), r.uifEmployee(), r.sdlAmount(), medicalMembers);

        return new PayrollResult(
                r.grossSalary().subtract(ot).subtract(payout), r.travelAllowance(), r.medicalAid(), r.pension(),
                r.payeAmount(), r.uifEmployee(), r.uifEmployer(), r.sdlAmount(),
                r.taxableIncome(), r.taxBeforeRebate(), r.primaryRebate(),
                r.secondaryRebate(), r.tertiaryRebate(), r.medicalTaxCredit(), r.taxYear(), ot, payout, bonus, backPay, noticePay);
    }

    /** The year to date before this month, for the averaging method. */
    public record Prior(BigDecimal taxable, BigDecimal paye, int months) {}

    /**
     * @param regularTaxable this month's regular taxable pay, kept on the payslip for the next month's year-to-date
     * @param regularPaye    the PAYE on regular pay this month, as the method worked it
     * @param periodicPaye   what the periodic method gives for regular pay, to show the difference
     */
    public record Calc(PayrollResult result, BigDecimal regularTaxable, BigDecimal regularPaye, BigDecimal periodicPaye) {}

    /**
     * As {@link #calculate(HrEmployee, StatutoryYearRules, BigDecimal, int, BigDecimal, Period)}, and with a {@code prior} also replaces the PAYE
     * on regular pay by the averaging method. The tax on a bonus, back pay or notice pay is the same either way.
     */
    public Calc calculate(HrEmployee employee, StatutoryYearRules rules, BigDecimal annualPayroll, int medicalMembers, BigDecimal overtime,
                          Period period, Prior prior) {
        PayrollResult full = calculate(employee, rules, annualPayroll, medicalMembers, overtime, period);
        BigDecimal ot = overtime == null ? BigDecimal.ZERO : overtime;
        BigDecimal payout = period.leavePayout() == null ? BigDecimal.ZERO : period.leavePayout();
        SarsPayrollResult regular = SarsPayeCalculator.calculate(new SarsPayeCalculator.Inputs(
                period.basic().add(ot).add(payout), period.travel(), period.pension(),
                employee.getMedicalAidContribution(), employee.getDateOfBirth(), medicalMembers, annualPayroll,
                period.employerMedical(), period.employerRetirement(), period.otherFringe(), BigDecimal.ZERO), rules);
        BigDecimal monthlyTaxable = regular.taxableIncome().divide(BigDecimal.valueOf(12), 2, java.math.RoundingMode.HALF_UP);
        BigDecimal periodic = regular.payeAmount();
        if (prior == null) return new Calc(full, monthlyTaxable, periodic, periodic);

        BigDecimal averaged = PayeAveragingRules.paye(monthlyTaxable, prior.taxable(), prior.paye(), prior.months(),
                regular.primaryRebate(), regular.secondaryRebate(), regular.tertiaryRebate(), regular.medicalTaxCredit(), rules);
        BigDecimal annualPaymentTax = full.payeAmount().subtract(periodic);
        BigDecimal paye = averaged.add(annualPaymentTax);
        PayrollResult r = new PayrollResult(full.grossSalary(), full.travelAllowance(), full.medicalAid(), full.pension(),
                paye, full.uifEmployee(), full.uifEmployer(), full.sdlAmount(), full.taxableIncome(), full.taxBeforeRebate(),
                full.primaryRebate(), full.secondaryRebate(), full.tertiaryRebate(), full.medicalTaxCredit(), full.taxYear(),
                full.overtime(), full.leavePayout(), full.bonus(), full.backPay(), full.noticePay());
        return new Calc(r, monthlyTaxable, averaged, periodic);
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
            BigDecimal secondaryRebate,
            BigDecimal tertiaryRebate,
            BigDecimal medicalTaxCredit,
            int taxYear,
            BigDecimal overtime,
            BigDecimal leavePayout,
            BigDecimal bonus,
            BigDecimal backPay,
            BigDecimal noticePay
    ) {
        /** Without pay in place of notice. */
        public PayrollResult(BigDecimal grossSalary, BigDecimal travelAllowance, BigDecimal medicalAid, BigDecimal pension,
                             BigDecimal payeAmount, BigDecimal uifEmployee, BigDecimal uifEmployer, BigDecimal sdlAmount,
                             BigDecimal taxableIncome, BigDecimal taxBeforeRebate, BigDecimal primaryRebate,
                             BigDecimal secondaryRebate, BigDecimal tertiaryRebate, BigDecimal medicalTaxCredit, int taxYear,
                             BigDecimal overtime, BigDecimal leavePayout, BigDecimal bonus, BigDecimal backPay) {
            this(grossSalary, travelAllowance, medicalAid, pension, payeAmount, uifEmployee, uifEmployer, sdlAmount, taxableIncome,
                    taxBeforeRebate, primaryRebate, secondaryRebate, tertiaryRebate, medicalTaxCredit, taxYear, overtime, leavePayout,
                    bonus, backPay, BigDecimal.ZERO);
        }

        /** Without a bonus or back pay. */
        public PayrollResult(BigDecimal grossSalary, BigDecimal travelAllowance, BigDecimal medicalAid, BigDecimal pension,
                             BigDecimal payeAmount, BigDecimal uifEmployee, BigDecimal uifEmployer, BigDecimal sdlAmount,
                             BigDecimal taxableIncome, BigDecimal taxBeforeRebate, BigDecimal primaryRebate,
                             BigDecimal secondaryRebate, BigDecimal tertiaryRebate, BigDecimal medicalTaxCredit, int taxYear,
                             BigDecimal overtime, BigDecimal leavePayout) {
            this(grossSalary, travelAllowance, medicalAid, pension, payeAmount, uifEmployee, uifEmployer, sdlAmount, taxableIncome,
                    taxBeforeRebate, primaryRebate, secondaryRebate, tertiaryRebate, medicalTaxCredit, taxYear, overtime, leavePayout,
                    BigDecimal.ZERO, BigDecimal.ZERO);
        }

        /** Without annual leave paid out. */
        public PayrollResult(BigDecimal grossSalary, BigDecimal travelAllowance, BigDecimal medicalAid, BigDecimal pension,
                             BigDecimal payeAmount, BigDecimal uifEmployee, BigDecimal uifEmployer, BigDecimal sdlAmount,
                             BigDecimal taxableIncome, BigDecimal taxBeforeRebate, BigDecimal primaryRebate,
                             BigDecimal secondaryRebate, BigDecimal tertiaryRebate, BigDecimal medicalTaxCredit, int taxYear, BigDecimal overtime) {
            this(grossSalary, travelAllowance, medicalAid, pension, payeAmount, uifEmployee, uifEmployer, sdlAmount, taxableIncome,
                    taxBeforeRebate, primaryRebate, secondaryRebate, tertiaryRebate, medicalTaxCredit, taxYear, overtime, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO);
        }

        /** Without overtime. */
        public PayrollResult(BigDecimal grossSalary, BigDecimal travelAllowance, BigDecimal medicalAid, BigDecimal pension,
                             BigDecimal payeAmount, BigDecimal uifEmployee, BigDecimal uifEmployer, BigDecimal sdlAmount,
                             BigDecimal taxableIncome, BigDecimal taxBeforeRebate, BigDecimal primaryRebate,
                             BigDecimal secondaryRebate, BigDecimal tertiaryRebate, BigDecimal medicalTaxCredit, int taxYear) {
            this(grossSalary, travelAllowance, medicalAid, pension, payeAmount, uifEmployee, uifEmployer, sdlAmount, taxableIncome,
                    taxBeforeRebate, primaryRebate, secondaryRebate, tertiaryRebate, medicalTaxCredit, taxYear, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO);
        }

        public BigDecimal totalEarnings() { return grossSalary.add(overtime).add(travelAllowance).add(leavePayout).add(bonus).add(backPay).add(noticePay); }

        public BigDecimal totalDeductions() {
            // the medical credit is already inside payeAmount
            return payeAmount.add(uifEmployee).add(medicalAid).add(pension);
        }

        public BigDecimal netPay() { return totalEarnings().subtract(totalDeductions()); }

        public BigDecimal totalRebates() { return primaryRebate.add(secondaryRebate).add(tertiaryRebate); }
    }
}
