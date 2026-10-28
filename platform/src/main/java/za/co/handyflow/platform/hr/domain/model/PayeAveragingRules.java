package za.co.handyflow.platform.hr.domain.model;

import za.co.handyflow.platform.shared.SarsPayeCalculator;
import za.co.handyflow.platform.shared.StatutoryYearRules;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * The averaging method for the regular pay of a year. Each month the pay so far is annualised (year to date / months gone x 12), the year's
 * tax is worked out on that with the full annual rebates and medical credit, spread over the months gone (/ 12 x months), and the PAYE already
 * paid on regular pay is taken off. Bonuses, back pay and notice pay stay outside it, taxed as annual payments on top.
 * <p>
 * With the same pay every month it gives exactly what the periodic method gives. With pay that goes up and down it follows the year, so
 * the total over the year is the tax the year bears. It never refunds: a month cannot go below nil, the difference is caught up later.
 */
public final class PayeAveragingRules {

    public static final String PERIODIC = "PERIODIC";
    public static final String AVERAGING = "AVERAGING";

    private PayeAveragingRules() {}

    public static boolean valid(String method) { return PERIODIC.equals(method) || AVERAGING.equals(method); }

    /**
     * Months the year-to-date covers: from the later of the start of the tax year and the month the person started, to the month of the pay date,
     * both counted. At least 1.
     */
    public static int monthsElapsed(LocalDate taxYearStart, LocalDate employeeStart, LocalDate payDate) {
        LocalDate from = employeeStart != null && employeeStart.isAfter(taxYearStart) ? employeeStart : taxYearStart;
        int months = (payDate.getYear() * 12 + payDate.getMonthValue()) - (from.getYear() * 12 + from.getMonthValue()) + 1;
        return Math.max(1, Math.min(12, months));
    }

    /**
     * The PAYE on this month's regular pay.
     *
     * @param monthlyTaxable this month's regular taxable pay (annual taxable / 12)
     * @param priorTaxable   regular taxable pay of the months before, this tax year
     * @param priorPaye      PAYE on regular pay of the months before, this tax year
     * @param months         months the year to date covers, this month included
     * @param primary        the annual rebates and credit this person gets (not pro-rated; the method spreads them)
     */
    public static BigDecimal paye(BigDecimal monthlyTaxable, BigDecimal priorTaxable, BigDecimal priorPaye, int months,
                                  BigDecimal primary, BigDecimal secondary, BigDecimal tertiary, BigDecimal annualCredit, StatutoryYearRules rules) {
        if (months < 1) throw new IllegalArgumentException("Months must be at least 1.");
        BigDecimal twelve = BigDecimal.valueOf(12), n = BigDecimal.valueOf(months);
        BigDecimal ytd = nvl(priorTaxable).add(monthlyTaxable);
        BigDecimal annualEquivalent = ytd.multiply(twelve).divide(n, 2, RoundingMode.HALF_UP);
        BigDecimal annualTax = SarsPayeCalculator.annualPaye(annualEquivalent, primary, secondary, tertiary, annualCredit, rules);
        BigDecimal liability = annualTax.multiply(n).divide(twelve, 2, RoundingMode.HALF_UP);
        return liability.subtract(nvl(priorPaye)).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * The extra tax a bonus, back pay, notice pay or leave payout bears: the year's tax on the annualised year to date with the payment added, less
     * the year's tax without it. Worked on the year to date rather than on this run's pay alone, so a second run in the same month that holds only
     * the extra payment is taxed on top of the salary already paid, not as if the person earned nothing.
     */
    public static BigDecimal annualPaymentTax(BigDecimal monthlyTaxable, BigDecimal priorTaxable, int months, BigDecimal payment,
                                              BigDecimal primary, BigDecimal secondary, BigDecimal tertiary, BigDecimal annualCredit,
                                              StatutoryYearRules rules) {
        if (nvl(payment).signum() <= 0) return BigDecimal.ZERO.setScale(2);
        BigDecimal annualEquivalent = nvl(priorTaxable).add(monthlyTaxable).multiply(BigDecimal.valueOf(12))
                .divide(BigDecimal.valueOf(months), 2, RoundingMode.HALF_UP);
        BigDecimal without = SarsPayeCalculator.annualPaye(annualEquivalent, primary, secondary, tertiary, annualCredit, rules);
        BigDecimal with = SarsPayeCalculator.annualPaye(annualEquivalent.add(payment), primary, secondary, tertiary, annualCredit, rules);
        return with.subtract(without).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal nvl(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }
}
