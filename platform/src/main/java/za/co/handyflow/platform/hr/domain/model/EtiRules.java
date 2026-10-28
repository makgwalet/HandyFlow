package za.co.handyflow.platform.hr.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Period;

/**
 * The employment tax incentive for one employee for one month. Pure: the table of amounts is passed in.
 * <p>
 * A person qualifies when they are 18 to 29 (at the end of the month), have a valid South African ID, are not excluded (domestic worker,
 * connected person), have not had 24 qualifying months, and are paid at least the full-month minimum and less than the cap for 160 hours.
 * A part month is grossed up to 160 hours to find the band, and the amount found is grossed back down by the same share.
 * The first 12 qualifying months earn the first-year amounts; the next 12 the second-year amounts (half).
 */
public final class EtiRules {

    public static final int MIN_AGE = 18, MAX_AGE = 29, MAX_MONTHS = 24, FIRST_PERIOD_MONTHS = 12;

    private EtiRules() {}

    public record Table(BigDecimal fullMonthMin, BigDecimal remunerationCap, BigDecimal maxFirst, BigDecimal maxSecond, BigDecimal taperStart,
                        BigDecimal taperRateFirst, BigDecimal taperRateSecond) {}

    /** The result for the month: the amount, the qualifying month it is (1 to 24), and when there is none, why. */
    public record Result(BigDecimal amount, Integer month, String why) {
        public boolean qualifies() { return month != null; }
    }

    private static Result no(String why) { return new Result(BigDecimal.ZERO.setScale(2), null, why); }

    public static int ageOn(LocalDate dateOfBirth, LocalDate date) {
        return Period.between(dateOfBirth, date).getYears();
    }

    /**
     * @param monthEnd         last day of the pay period
     * @param idValid          a valid South African ID number is on record
     * @param priorMonths      qualifying months already used: those claimed elsewhere or before, plus earlier payslips here
     * @param remuneration     the month's remuneration as paid
     * @param paidShare        the share of a full month actually paid, 1 for a full month (more than 0 and at most 1)
     */
    public static Result calculate(Table t, LocalDate dateOfBirth, LocalDate monthEnd, boolean idValid, String excludedReason,
                                   int priorMonths, BigDecimal remuneration, BigDecimal paidShare) {
        if (excludedReason != null && !excludedReason.isBlank()) return no("excluded: " + excludedReason.trim());
        if (dateOfBirth == null) return no("no date of birth on record");
        int age = ageOn(dateOfBirth, monthEnd);
        if (age < MIN_AGE || age > MAX_AGE) return no("age " + age + " is outside 18 to 29");
        if (!idValid) return no("no valid South African ID number on record");
        if (priorMonths >= MAX_MONTHS) return no("the 24 months have been used");
        if (remuneration == null || remuneration.signum() <= 0) return no("no remuneration this month");
        if (paidShare == null || paidShare.signum() <= 0 || paidShare.compareTo(BigDecimal.ONE) > 0) return no("no hours worked this month");

        BigDecimal full = remuneration.divide(paidShare, 2, RoundingMode.HALF_UP);   // grossed up to 160 hours
        if (full.compareTo(t.fullMonthMin()) < 0)
            return no("pay for a full month would be " + full.toPlainString() + ", below the minimum of " + t.fullMonthMin().toPlainString());
        if (full.compareTo(t.remunerationCap()) >= 0)
            return no("pay for a full month is " + full.toPlainString() + ", at or above the cap of " + t.remunerationCap().toPlainString());

        int month = priorMonths + 1;
        boolean first = month <= FIRST_PERIOD_MONTHS;
        BigDecimal max = first ? t.maxFirst() : t.maxSecond();
        BigDecimal taper = first ? t.taperRateFirst() : t.taperRateSecond();
        BigDecimal fullAmount = full.compareTo(t.taperStart()) <= 0
                ? max
                : max.subtract(full.subtract(t.taperStart()).multiply(taper)).max(BigDecimal.ZERO);
        BigDecimal amount = fullAmount.multiply(paidShare).setScale(2, RoundingMode.HALF_UP);
        return new Result(amount, month, null);
    }

    /** The share of a full month that was paid: days paid over working days, never more than 1. */
    public static BigDecimal paidShare(BigDecimal daysPaid, int workingDays) {
        if (workingDays <= 0 || daysPaid == null) return BigDecimal.ONE;
        BigDecimal share = daysPaid.divide(BigDecimal.valueOf(workingDays), 6, RoundingMode.HALF_UP);
        return share.min(BigDecimal.ONE);
    }

    /** The ETI that reduces this return's PAYE, and what is left over to carry forward. Never more than the PAYE. */
    public static BigDecimal[] split(BigDecimal etiWorkedOut, BigDecimal payeOfReturn) {
        BigDecimal used = etiWorkedOut.min(payeOfReturn.max(BigDecimal.ZERO)).max(BigDecimal.ZERO);
        return new BigDecimal[]{used.setScale(2, RoundingMode.HALF_UP), etiWorkedOut.subtract(used).setScale(2, RoundingMode.HALF_UP)};
    }
}
