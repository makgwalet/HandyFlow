package za.co.handyflow.platform.warehousing.application.internal;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * South African public holidays for a year, worked out from the Public Holidays Act: twelve holidays, two of them tied to Easter, and a holiday that falls on a
 * Sunday is observed on the next day that is not itself a holiday. Pure, no database. Holidays the President declares for one year (an election day, say) cannot
 * be worked out and are added by hand.
 */
public final class WhseSaHolidays {
    private WhseSaHolidays() {}

    public record Holiday(LocalDate date, String name) {}

    /** Western Easter Sunday (anonymous Gregorian algorithm). */
    static LocalDate easter(int year) {
        int a = year % 19, b = year / 100, c = year % 100, d = b / 4, e = b % 4, f = (b + 8) / 25, g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30, i = c / 4, k = c % 4, l = (32 + 2 * e + 2 * i - h - k) % 7, m = (a + 11 * h + 22 * l) / 451;
        int month = (h + l - 7 * m + 114) / 31, day = ((h + l - 7 * m + 114) % 31) + 1;
        return LocalDate.of(year, month, day);
    }

    /** Every public holiday date in the year, in date order, including the Monday (or next free day) observed for one that falls on a Sunday. */
    public static List<Holiday> forYear(int year) {
        if (year < 1994 || year > 2200) throw new IllegalArgumentException("South African public holidays are available for 1994 to 2200.");
        LocalDate easter = easter(year);
        List<Holiday> nominal = List.of(
                new Holiday(LocalDate.of(year, 1, 1), "New Year's Day"),
                new Holiday(LocalDate.of(year, 3, 21), "Human Rights Day"),
                new Holiday(easter.minusDays(2), "Good Friday"),
                new Holiday(easter.plusDays(1), "Family Day"),
                new Holiday(LocalDate.of(year, 4, 27), "Freedom Day"),
                new Holiday(LocalDate.of(year, 5, 1), "Workers' Day"),
                new Holiday(LocalDate.of(year, 6, 16), "Youth Day"),
                new Holiday(LocalDate.of(year, 8, 9), "National Women's Day"),
                new Holiday(LocalDate.of(year, 9, 24), "Heritage Day"),
                new Holiday(LocalDate.of(year, 12, 16), "Day of Reconciliation"),
                new Holiday(LocalDate.of(year, 12, 25), "Christmas Day"),
                new Holiday(LocalDate.of(year, 12, 26), "Day of Goodwill"));
        Set<LocalDate> taken = new HashSet<>();
        nominal.forEach(h -> taken.add(h.date()));
        List<Holiday> out = new ArrayList<>(nominal);
        for (Holiday h : nominal) {
            if (h.date().getDayOfWeek() != DayOfWeek.SUNDAY) continue;
            LocalDate observed = h.date().plusDays(1);
            while (taken.contains(observed)) observed = observed.plusDays(1);
            taken.add(observed);
            out.add(new Holiday(observed, h.name() + " (observed)"));
        }
        out.sort(java.util.Comparator.comparing(Holiday::date));
        return out;
    }
}
