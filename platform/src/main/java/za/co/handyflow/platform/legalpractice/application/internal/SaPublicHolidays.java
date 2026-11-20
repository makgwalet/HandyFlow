package za.co.handyflow.platform.legalpractice.application.internal;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * South African public holidays, worked out from the rules in the Public Holidays Act 36 of 1994: the fixed days, Good
 * Friday and Family Day (Easter Monday), and the Monday after any holiday that falls on a Sunday.
 * <p>
 * Days the President proclaims for one year only (an election day, a one-off extra holiday) cannot be worked out. A firm
 * adds those as court-calendar entries of kind PUBLIC_HOLIDAY. Where a Sunday holiday's Monday is already a holiday, no
 * second day is added here; if one is proclaimed it is another such entry.
 */
public final class SaPublicHolidays {

    public static final int FIRST_YEAR = 1995;
    public static final int LAST_YEAR = 2200;

    private static final Map<Integer, Map<LocalDate, String>> CACHE = new ConcurrentHashMap<>();

    private SaPublicHolidays() {}

    /** The holiday's name if {@code d} is a public holiday, otherwise null. */
    public static String nameOf(LocalDate d) {
        if (d.getYear() < FIRST_YEAR || d.getYear() > LAST_YEAR) return null;
        // A Sunday holiday in late December moves to the Monday of the same year; one on 31 Dec cannot happen.
        return forYear(d.getYear()).get(d);
    }

    public static Map<LocalDate, String> forYear(int year) {
        if (year < FIRST_YEAR || year > LAST_YEAR) {
            throw new IllegalArgumentException("Public holidays are worked out for " + FIRST_YEAR + " to " + LAST_YEAR);
        }
        return CACHE.computeIfAbsent(year, SaPublicHolidays::build);
    }

    private static Map<LocalDate, String> build(int y) {
        LocalDate easter = easterSunday(y);
        Map<LocalDate, String> fixed = new HashMap<>();
        fixed.put(LocalDate.of(y, 1, 1), "New Year's Day");
        fixed.put(LocalDate.of(y, 3, 21), "Human Rights Day");
        fixed.put(easter.minusDays(2), "Good Friday");
        fixed.put(easter.plusDays(1), "Family Day");
        fixed.put(LocalDate.of(y, 4, 27), "Freedom Day");
        fixed.put(LocalDate.of(y, 5, 1), "Workers' Day");
        fixed.put(LocalDate.of(y, 6, 16), "Youth Day");
        fixed.put(LocalDate.of(y, 8, 9), "National Women's Day");
        fixed.put(LocalDate.of(y, 9, 24), "Heritage Day");
        fixed.put(LocalDate.of(y, 12, 16), "Day of Reconciliation");
        fixed.put(LocalDate.of(y, 12, 25), "Christmas Day");
        fixed.put(LocalDate.of(y, 12, 26), "Day of Goodwill");
        Map<LocalDate, String> all = new HashMap<>(fixed);
        for (Map.Entry<LocalDate, String> e : fixed.entrySet()) {
            if (e.getKey().getDayOfWeek() == DayOfWeek.SUNDAY) {
                LocalDate monday = e.getKey().plusDays(1);
                if (!fixed.containsKey(monday)) all.put(monday, e.getValue() + " (observed)");
            }
        }
        return Map.copyOf(all);
    }

    /** Anonymous Gregorian algorithm (Meeus/Jones/Butcher). */
    static LocalDate easterSunday(int y) {
        int a = y % 19, b = y / 100, c = y % 100, d = b / 4, e = b % 4, f = (b + 8) / 25, g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30, i = c / 4, k = c % 4, l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int month = (h + l - 7 * m + 114) / 31, day = ((h + l - 7 * m + 114) % 31) + 1;
        return LocalDate.of(y, month, day);
    }
}
