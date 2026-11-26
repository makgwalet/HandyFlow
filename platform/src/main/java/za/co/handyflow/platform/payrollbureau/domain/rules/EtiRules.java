package za.co.handyflow.platform.payrollbureau.domain.rules;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * Employment Tax Incentive (ETI) calculation (phase 4). Pure rules, no database.
 *
 * <p>The bands are data, not code: a {@link RuleSet} has an effective date and a list of bands, so a change SARS announces
 * is a new rule set rather than an edit to this class. The seeded set is the one in force from 1 April 2025, taken from
 * SARS's ETI guide (ETI01, issue 5) and SARS's ETI page. Check it against SARS before relying on it for a real filing.
 *
 * <p>Inside a band, the incentive for a full month is {@code base + rate x (remuneration - pivot)}, separately for the
 * first and second 12 qualifying months.
 */
public final class EtiRules {

    public static final String SA_ID = "SA_ID";
    public static final String ASYLUM = "ASYLUM";
    public static final String REFUGEE = "REFUGEE";
    public static final String OTHER = "OTHER";

    public static final String FIRST_YEAR = "FIRST_12";
    public static final String SECOND_YEAR = "SECOND_12";

    /** Hours in a full month for the part-month rule. */
    public static final BigDecimal FULL_MONTH_HOURS = new BigDecimal("160");
    /** The most months a single employment can ever claim. */
    public static final int MAX_MONTHS = 24;
    public static final int FIRST_YEAR_MONTHS = 12;

    /** Remuneration from {@code lower} (inclusive) up to {@code upper} (exclusive; null = no limit). */
    public record Band(BigDecimal lower, BigDecimal upper,
                       BigDecimal firstBase, BigDecimal firstRate,
                       BigDecimal secondBase, BigDecimal secondRate,
                       BigDecimal pivot) {}

    public record RuleSet(String version, LocalDate effectiveFrom, LocalDate endsOn,
                          int minAge, int maxAge, LocalDate employedOnOrAfter, List<Band> bands) {}

    /** What the calculation needs to know about one employee for one month. */
    public record Facts(String idKind, LocalDate dateOfBirth, LocalDate employedFrom,
                        boolean domesticWorker, boolean connectedPerson,
                        int monthsAlreadyClaimed, boolean meetsMinimumWage,
                        BigDecimal remuneration, BigDecimal hoursWorked) {}

    public record Result(boolean eligible, List<String> reasons, String tier, BigDecimal remunerationUsed,
                         BigDecimal fullMonthIncentive, BigDecimal incentive, String ruleVersion) {}

    private static BigDecimal d(String s) { return new BigDecimal(s); }

    /** The rule set in force from 1 April 2025. */
    public static final RuleSet FROM_2025_04_01 = new RuleSet(
            "eti-2025-04-01", LocalDate.of(2025, 4, 1), LocalDate.of(2029, 2, 28), 18, 29, LocalDate.of(2013, 10, 1),
            List.of(
                    new Band(d("0"), d("2500"), d("0"), d("0.60"), d("0"), d("0.30"), d("0")),
                    new Band(d("2500"), d("5500"), d("1500"), d("0"), d("750"), d("0"), d("0")),
                    new Band(d("5500"), d("7500"), d("1500"), d("-0.75"), d("750"), d("-0.375"), d("5500"))));

    private EtiRules() {}

    private static BigDecimal money(BigDecimal v) { return v.setScale(2, RoundingMode.HALF_UP); }

    /** Age on the last day of the month, which is when SARS tests it. */
    public static int ageAtEndOfMonth(LocalDate dateOfBirth, YearMonth month) {
        return java.time.Period.between(dateOfBirth, month.atEndOfMonth()).getYears();
    }

    /** The incentive for a claim month. A reason is given for every test that fails; the amount is zero when any fails. */
    public static Result calculate(Facts f, YearMonth month, RuleSet rules) {
        List<String> reasons = new ArrayList<>();
        LocalDate monthEnd = month.atEndOfMonth();
        if (month.atDay(1).isBefore(rules.effectiveFrom())) reasons.add("RULES_NOT_IN_FORCE");
        if (monthEnd.isAfter(rules.endsOn())) reasons.add("INCENTIVE_ENDED");
        if (f.idKind() == null || OTHER.equals(f.idKind())) reasons.add("ID_NOT_QUALIFYING");
        if (f.dateOfBirth() == null) {
            reasons.add("NO_DATE_OF_BIRTH");
        } else {
            int age = ageAtEndOfMonth(f.dateOfBirth(), month);
            if (age < rules.minAge() || age > rules.maxAge()) reasons.add("AGE_OUT_OF_RANGE");
        }
        if (f.employedFrom() == null) reasons.add("NO_START_DATE");
        else if (f.employedFrom().isBefore(rules.employedOnOrAfter())) reasons.add("EMPLOYED_BEFORE_SCHEME");
        if (f.domesticWorker()) reasons.add("DOMESTIC_WORKER");
        if (f.connectedPerson()) reasons.add("CONNECTED_PERSON");
        if (!f.meetsMinimumWage()) reasons.add("BELOW_MINIMUM_WAGE");
        if (f.monthsAlreadyClaimed() >= MAX_MONTHS) reasons.add("MONTHS_EXHAUSTED");
        BigDecimal hours = f.hoursWorked();
        if (hours != null && hours.signum() <= 0) reasons.add("NO_HOURS_WORKED");
        BigDecimal rem = f.remuneration() == null ? BigDecimal.ZERO : f.remuneration();
        if (rem.signum() <= 0) reasons.add("NO_REMUNERATION");

        String tier = f.monthsAlreadyClaimed() < FIRST_YEAR_MONTHS ? FIRST_YEAR : SECOND_YEAR;
        if (!reasons.isEmpty()) return new Result(false, reasons, tier, rem, BigDecimal.ZERO, BigDecimal.ZERO, rules.version());

        boolean partMonth = hours != null && hours.compareTo(FULL_MONTH_HOURS) < 0;
        BigDecimal used = partMonth ? rem.multiply(FULL_MONTH_HOURS).divide(hours, 10, RoundingMode.HALF_UP) : rem;
        BigDecimal full = BigDecimal.ZERO;
        boolean inBand = false;
        for (Band b : rules.bands()) {
            if (used.compareTo(b.lower()) >= 0 && (b.upper() == null || used.compareTo(b.upper()) < 0)) {
                BigDecimal base = FIRST_YEAR.equals(tier) ? b.firstBase() : b.secondBase();
                BigDecimal rate = FIRST_YEAR.equals(tier) ? b.firstRate() : b.secondRate();
                full = base.add(rate.multiply(used.subtract(b.pivot())));
                inBand = true;
                break;
            }
        }
        if (!inBand) {
            reasons.add("REMUNERATION_ABOVE_LIMIT");
            return new Result(false, reasons, tier, money(used), BigDecimal.ZERO, BigDecimal.ZERO, rules.version());
        }
        if (full.signum() < 0) full = BigDecimal.ZERO;
        BigDecimal actual = partMonth ? full.multiply(hours).divide(FULL_MONTH_HOURS, 10, RoundingMode.HALF_UP) : full;
        return new Result(true, reasons, tier, money(used), money(full), money(actual), rules.version());
    }

    /** What is actually taken off the PAYE for the month: no more than the PAYE, with the rest carried forward. */
    public record Applied(BigDecimal used, BigDecimal carriedForward) {}

    public static Applied applyToPaye(BigDecimal incentiveThisMonth, BigDecimal broughtForward, BigDecimal payeThisMonth) {
        BigDecimal available = incentiveThisMonth.add(broughtForward == null ? BigDecimal.ZERO : broughtForward);
        BigDecimal paye = payeThisMonth.signum() < 0 ? BigDecimal.ZERO : payeThisMonth;
        BigDecimal used = available.min(paye);
        return new Applied(money(used), money(available.subtract(used)));
    }
}
