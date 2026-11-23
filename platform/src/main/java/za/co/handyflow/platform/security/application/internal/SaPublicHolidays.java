package za.co.handyflow.platform.security.application.internal;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * South Africa's public holidays (Public Holidays Act 36 of 1994). Pure.
 *
 * Twelve a year: ten on fixed dates and two that follow Easter. When a holiday falls on a Sunday, the Monday after it is a public holiday too.
 * Days declared by proclamation (an election day, a one-off) are not in the Act; a company adds those itself.
 * One case the Act leaves to a proclamation is not worked out here: a Sunday holiday whose following Monday is already a holiday
 * (Christmas Day on a Sunday, with the Day of Goodwill on the Monday). The company adds the extra day.
 */
final class SaPublicHolidays {
    private SaPublicHolidays() {}

    /** The holidays of one year, by date, in date order. */
    static Map<LocalDate, String> of(int year) {
        LocalDate easter = easterSunday(year);
        Map<LocalDate, String> fixedAndEaster = new LinkedHashMap<>();
        fixedAndEaster.put(LocalDate.of(year, 1, 1), "New Year's Day");
        fixedAndEaster.put(LocalDate.of(year, 3, 21), "Human Rights Day");
        fixedAndEaster.put(easter.minusDays(2), "Good Friday");
        fixedAndEaster.put(easter.plusDays(1), "Family Day");
        fixedAndEaster.put(LocalDate.of(year, 4, 27), "Freedom Day");
        fixedAndEaster.put(LocalDate.of(year, 5, 1), "Workers' Day");
        fixedAndEaster.put(LocalDate.of(year, 6, 16), "Youth Day");
        fixedAndEaster.put(LocalDate.of(year, 8, 9), "National Women's Day");
        fixedAndEaster.put(LocalDate.of(year, 9, 24), "Heritage Day");
        fixedAndEaster.put(LocalDate.of(year, 12, 16), "Day of Reconciliation");
        fixedAndEaster.put(LocalDate.of(year, 12, 25), "Christmas Day");
        fixedAndEaster.put(LocalDate.of(year, 12, 26), "Day of Goodwill");

        Map<LocalDate, String> out = new TreeMap<>(fixedAndEaster);
        for (Map.Entry<LocalDate, String> e : fixedAndEaster.entrySet()) {
            if (e.getKey().getDayOfWeek() == DayOfWeek.SUNDAY) {
                LocalDate monday = e.getKey().plusDays(1);
                // a Monday that is already a holiday is left as it is (see the class note)
                out.putIfAbsent(monday, e.getValue() + " (observed)");
            }
        }
        return out;
    }

    /** The name of the holiday on this date, or null when it is an ordinary day. */
    static String nameOn(LocalDate date) { return of(date.getYear()).get(date); }

    /**
     * Easter Sunday by the Gregorian computus (the "anonymous" algorithm, valid for every Gregorian year).
     */
    static LocalDate easterSunday(int year) {
        int a = year % 19, b = year / 100, c = year % 100, d = b / 4, e = b % 4, f = (b + 8) / 25, g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30, i = c / 4, k = c % 4, l = (32 + 2 * e + 2 * i - h - k) % 7, m = (a + 11 * h + 22 * l) / 451;
        int month = (h + l - 7 * m + 114) / 31, day = ((h + l - 7 * m + 114) % 31) + 1;
        return LocalDate.of(year, month, day);
    }
}
