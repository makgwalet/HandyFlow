package za.co.handyflow.platform.debtcollection.application.internal;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;

/** Business-day arithmetic: Monday–Friday, minus the supplied public holidays. Pure and testable. */
public final class BusinessDays {

    private BusinessDays() {}

    public static boolean isBusinessDay(LocalDate d, Set<LocalDate> holidays) {
        DayOfWeek w = d.getDayOfWeek();
        return w != DayOfWeek.SATURDAY && w != DayOfWeek.SUNDAY && !holidays.contains(d);
    }

    /** The date on which {@code n} business days have elapsed after {@code from} (from itself is day 0). */
    public static LocalDate addBusinessDays(LocalDate from, int n, Set<LocalDate> holidays) {
        if (n < 0) throw new IllegalArgumentException("n must be >= 0");
        LocalDate d = from;
        int counted = 0;
        while (counted < n) {
            d = d.plusDays(1);
            if (isBusinessDay(d, holidays)) counted++;
        }
        return d;
    }
}
