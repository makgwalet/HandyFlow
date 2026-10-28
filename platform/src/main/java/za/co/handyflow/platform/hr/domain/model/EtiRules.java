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

    // ── Reporting on the tax certificate ────────────────────────────────────────────────────────────

    /** The hours a full month counts as, and the most SARS takes in a month. */
    public static final int FULL_MONTH_HOURS = 160;

    /**
     * The national minimum wage per hour in the tax year (named by the year it ends), from 1 March of the year before: R25.42 from March
     * 2023, R27.58 from March 2024, R28.79 from March 2025 and R30.23 from March 2026. A year not known yet takes the latest known.
     */
    public static BigDecimal nationalMinimumWage(int taxYear) {
        if (taxYear <= 2024) return new BigDecimal("25.42");
        if (taxYear == 2025) return new BigDecimal("27.58");
        if (taxYear == 2026) return new BigDecimal("28.79");
        return new BigDecimal("30.23");
    }

    /** What a payslip says about the month, for the ETI information on the certificate. */
    public record Slip(LocalDate periodEnd, BigDecimal remuneration, BigDecimal eti, Integer etiMonth, BigDecimal daysPaid, int workingDays) {}

    /** One month of ETI information (SARS codes 7002 to 7008). {@code cycle}: 0 not qualifying, 1 first 12 months, 2 second 12 months. */
    public record Month(int calendarMonth, BigDecimal remuneration, BigDecimal minimumWage, BigDecimal eti, int cycle, int hours, BigDecimal wagePaid) {}

    /**
     * The ETI information to report for a person over a certificate's payslips, oldest month first. Nothing when ETI was not claimed in
     * any month (the certificate's ETI indicator is then N and no ETI codes are written). When it was, every month with a payslip is
     * reported, those without a claim with a nil amount and cycle 0.
     * <p>
     * Hours are the share of a full month paid out of 160, and the minimum wage is the national hourly minimum for those hours: the
     * payslip holds no hours, so both follow from the days paid. The wage paid is the month's remuneration.
     */
    public static java.util.List<Month> months(java.util.List<Slip> slips, int taxYear) {
        boolean any = slips.stream().anyMatch(s -> s.eti() != null && s.eti().signum() > 0);
        if (!any) return java.util.List.of();
        java.util.TreeMap<java.time.YearMonth, java.util.List<Slip>> byMonth = new java.util.TreeMap<>();
        for (Slip s : slips) byMonth.computeIfAbsent(java.time.YearMonth.from(s.periodEnd()), k -> new java.util.ArrayList<>()).add(s);
        BigDecimal wage = nationalMinimumWage(taxYear);
        java.util.List<Month> out = new java.util.ArrayList<>();
        for (var e : byMonth.entrySet()) {
            BigDecimal remuneration = BigDecimal.ZERO, eti = BigDecimal.ZERO, share = BigDecimal.ZERO;
            int cycle = 0;
            for (Slip s : e.getValue()) {
                remuneration = remuneration.add(s.remuneration() == null ? BigDecimal.ZERO : s.remuneration());
                if (s.eti() != null && s.eti().signum() > 0) {
                    eti = eti.add(s.eti());
                    cycle = s.etiMonth() != null && s.etiMonth() > FIRST_PERIOD_MONTHS ? 2 : 1;
                }
                share = share.add(paidShare(s.daysPaid(), s.workingDays()));
            }
            int hours = Math.min(FULL_MONTH_HOURS, share.multiply(BigDecimal.valueOf(FULL_MONTH_HOURS)).setScale(0, RoundingMode.HALF_UP).intValue());
            if (cycle > 0 && hours < 1) hours = 1;
            BigDecimal minimum = wage.multiply(BigDecimal.valueOf(hours)).setScale(2, RoundingMode.HALF_UP);
            out.add(new Month(e.getKey().getMonthValue(), remuneration.setScale(2, RoundingMode.HALF_UP), minimum, eti.setScale(2, RoundingMode.HALF_UP),
                    cycle, hours, remuneration.setScale(2, RoundingMode.HALF_UP)));
        }
        return out;
    }
}
