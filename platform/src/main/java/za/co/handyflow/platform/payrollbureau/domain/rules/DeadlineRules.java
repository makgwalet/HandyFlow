package za.co.handyflow.platform.payrollbureau.domain.rules;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.function.Predicate;

/**
 * SARS due-date rule: when a due date falls on a weekend or public holiday, the obligation falls due on the last
 * business day before it (SARS calendar and the EMP201 guidance). Pure, so it can be tested without a database.
 */
public final class DeadlineRules {
    private DeadlineRules() {}

    public static LocalDate lastBusinessDayOnOrBefore(LocalDate date, Predicate<LocalDate> isPublicHoliday) {
        LocalDate d = date;
        while (d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY || isPublicHoliday.test(d)) {
            d = d.minusDays(1);
        }
        return d;
    }
}
