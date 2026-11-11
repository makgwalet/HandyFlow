package za.co.handyflow.platform.warehousing.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WhseSaHolidaysTest {

    private static boolean has(List<WhseSaHolidays.Holiday> l, LocalDate d, String name) {
        return l.stream().anyMatch(h -> h.date().equals(d) && h.name().equals(name));
    }

    @Test @DisplayName("Easter Sunday is worked out correctly")
    void easter() {
        assertEquals(LocalDate.of(2026, 4, 5), WhseSaHolidays.easter(2026));
        assertEquals(LocalDate.of(2027, 3, 28), WhseSaHolidays.easter(2027));
        assertEquals(LocalDate.of(2024, 3, 31), WhseSaHolidays.easter(2024));
        assertEquals(LocalDate.of(2025, 4, 20), WhseSaHolidays.easter(2025));
    }

    @Test @DisplayName("2026 has the twelve holidays, Good Friday on 3 April, Family Day on 6 April, and Women's Day (a Sunday) observed on 10 August")
    void year2026() {
        List<WhseSaHolidays.Holiday> l = WhseSaHolidays.forYear(2026);
        assertTrue(has(l, LocalDate.of(2026, 4, 3), "Good Friday"));
        assertTrue(has(l, LocalDate.of(2026, 4, 6), "Family Day"));
        assertTrue(has(l, LocalDate.of(2026, 12, 16), "Day of Reconciliation"));
        assertTrue(has(l, LocalDate.of(2026, 8, 10), "National Women's Day (observed)"));
        assertEquals(13, l.size()); // 12, plus the Monday for Women's Day
    }

    @Test @DisplayName("a holiday on a Sunday is observed on the Monday: Workers' Day 2022 was Sunday 1 May")
    void sundayToMonday() {
        List<WhseSaHolidays.Holiday> l = WhseSaHolidays.forYear(2022);
        assertTrue(has(l, LocalDate.of(2022, 5, 2), "Workers' Day (observed)"));
    }

    @Test @DisplayName("when the Monday is itself a holiday, the observed day moves on: Christmas 2022 fell on a Sunday, so 27 December")
    void collision() {
        List<WhseSaHolidays.Holiday> l = WhseSaHolidays.forYear(2022);
        assertTrue(has(l, LocalDate.of(2022, 12, 26), "Day of Goodwill"));
        assertTrue(has(l, LocalDate.of(2022, 12, 27), "Christmas Day (observed)"));
    }

    @Test @DisplayName("results are in date order with no date twice")
    void ordered() {
        for (int y : new int[]{2022, 2026, 2027, 2033}) {
            List<WhseSaHolidays.Holiday> l = WhseSaHolidays.forYear(y);
            for (int i = 1; i < l.size(); i++) assertTrue(l.get(i).date().isAfter(l.get(i - 1).date()), y + " " + l.get(i));
        }
    }

    @Test @DisplayName("a year before 1994 or absurdly late is refused")
    void range() {
        assertThrows(IllegalArgumentException.class, () -> WhseSaHolidays.forYear(1990));
        assertThrows(IllegalArgumentException.class, () -> WhseSaHolidays.forYear(3000));
    }
}
