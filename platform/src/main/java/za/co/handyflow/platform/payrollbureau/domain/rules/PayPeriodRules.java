package za.co.handyflow.platform.payrollbureau.domain.rules;

import za.co.handyflow.platform.shared.StatutoryYearRules;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Pay periods of a pay group (phase 1, slice 1c). Monthly periods are calendar months. Fortnightly and weekly periods are
 * 14 and 7 days long, counted from the group's anchor date (the first day of any period).
 */
public final class PayPeriodRules {

    public record Period(LocalDate start, LocalDate end) {}

    private PayPeriodRules() {}

    public static boolean knownFrequency(String f) {
        return "MONTHLY".equals(f) || "FORTNIGHTLY".equals(f) || "WEEKLY".equals(f);
    }

    /** Why a group cannot be created like this, or null. */
    public static String groupProblem(String name, String frequency, LocalDate anchor, int payLagDays) {
        if (name == null || name.isBlank()) return "Give the pay group a name.";
        if (!knownFrequency(frequency)) return "Pay frequency must be MONTHLY, FORTNIGHTLY or WEEKLY.";
        if (!"MONTHLY".equals(frequency) && anchor == null) return "Weekly and fortnightly groups need the first day of a pay period.";
        if (payLagDays < 0 || payLagDays > 31) return "Days between period end and pay day must be 0 to 31.";
        return null;
    }

    private static int length(String frequency) {
        return "WEEKLY".equals(frequency) ? 7 : 14;
    }

    /** The period that contains {@code date}. */
    public static Period periodContaining(String frequency, LocalDate anchor, LocalDate date) {
        if ("MONTHLY".equals(frequency)) {
            LocalDate start = date.withDayOfMonth(1);
            return new Period(start, start.plusMonths(1).minusDays(1));
        }
        int len = length(frequency);
        long days = ChronoUnit.DAYS.between(anchor, date);
        LocalDate start = anchor.plusDays(Math.floorDiv(days, (long) len) * len);
        return new Period(start, start.plusDays(len - 1L));
    }

    /** The period right after the one that ends on {@code previousEnd}. */
    public static Period periodAfter(String frequency, LocalDate anchor, LocalDate previousEnd) {
        return periodContaining(frequency, anchor, previousEnd.plusDays(1));
    }

    /** Why a pay run cannot cover start to end for this group, or null: it must be exactly one period of the group. */
    public static String periodProblem(String frequency, LocalDate anchor, LocalDate start, LocalDate end) {
        Period expected = periodContaining(frequency, anchor, start);
        if (!expected.start().equals(start) || !expected.end().equals(end)) {
            return "This group's period containing " + start + " runs from " + expected.start() + " to " + expected.end()
                    + ". A pay run must cover exactly one period.";
        }
        return null;
    }

    public static LocalDate payDateFor(Period period, int payLagDays) {
        return period.end().plusDays(payLagDays);
    }

    /** SARS taxes pay in the tax year it is paid, so a group's runs take the tax year from the pay date. */
    public static int taxYearOfPayDate(LocalDate payDate) {
        return StatutoryYearRules.taxYearOf(payDate);
    }
}
