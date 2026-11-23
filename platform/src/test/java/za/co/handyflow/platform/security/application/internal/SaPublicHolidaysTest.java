package za.co.handyflow.platform.security.application.internal;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SaPublicHolidaysTest {

    private static LocalDate d(String s) { return LocalDate.parse(s); }

    @Test
    void easterSundayIsRightForKnownYears() {
        assertEquals(d("2000-04-23"), SaPublicHolidays.easterSunday(2000));
        assertEquals(d("2011-04-24"), SaPublicHolidays.easterSunday(2011));
        assertEquals(d("2015-04-05"), SaPublicHolidays.easterSunday(2015));
        assertEquals(d("2018-04-01"), SaPublicHolidays.easterSunday(2018));
        assertEquals(d("2019-04-21"), SaPublicHolidays.easterSunday(2019));
        assertEquals(d("2024-03-31"), SaPublicHolidays.easterSunday(2024));
        assertEquals(d("2025-04-20"), SaPublicHolidays.easterSunday(2025));
        assertEquals(d("2026-04-05"), SaPublicHolidays.easterSunday(2026));
        assertEquals(d("2038-04-25"), SaPublicHolidays.easterSunday(2038));
    }

    @Test
    void easterIsAlwaysASunday() {
        for (int y = 1950; y <= 2100; y++) assertEquals(DayOfWeek.SUNDAY, SaPublicHolidays.easterSunday(y).getDayOfWeek(), "year " + y);
    }

    @Test
    void goodFridayAndFamilyDayFollowEaster() {
        Map<LocalDate, String> h = SaPublicHolidays.of(2026);
        assertEquals("Good Friday", h.get(d("2026-04-03")));
        assertEquals("Family Day", h.get(d("2026-04-06")));
        Map<LocalDate, String> h24 = SaPublicHolidays.of(2024);
        assertEquals("Good Friday", h24.get(d("2024-03-29")));
        assertEquals("Family Day", h24.get(d("2024-04-01")));
    }

    @Test
    void everyHolidayOnASundayHasAHolidayMondayAfterIt() {
        int checked = 0;
        for (int y = 2000; y <= 2060; y++) {
            Map<LocalDate, String> h = SaPublicHolidays.of(y);
            for (LocalDate x : h.keySet()) {
                if (x.getDayOfWeek() == DayOfWeek.SUNDAY) { assertTrue(h.containsKey(x.plusDays(1)), x.toString()); checked++; }
            }
        }
        assertTrue(checked > 20, "the check must have met Sunday holidays");
    }

    @Test
    void theSundayItselfStaysAHoliday() {
        assertNotNull(SaPublicHolidays.nameOn(d("2026-08-09")));
    }

    @Test
    void atLeastTwelveAYear() {
        for (int y = 2000; y <= 2060; y++) assertTrue(SaPublicHolidays.of(y).size() >= 12, "year " + y);
    }

    @Test
    void aHolidayOnASundayAlsoGivesTheMonday() {
        Map<LocalDate, String> h = SaPublicHolidays.of(2026);
        assertEquals("National Women's Day", h.get(d("2026-08-09")));
        assertEquals("National Women's Day (observed)", h.get(d("2026-08-10")));
        Map<LocalDate, String> h23 = SaPublicHolidays.of(2023);
        assertEquals("New Year's Day (observed)", h23.get(d("2023-01-02")));
        Map<LocalDate, String> h21 = SaPublicHolidays.of(2021);
        assertEquals("Day of Goodwill (observed)", h21.get(d("2021-12-27")));
    }

    @Test
    void aMondayThatIsAlreadyAHolidayKeepsItsOwnName() {
        // 2022: Christmas Day is a Sunday and the Monday is the Day of Goodwill. The Act's extra day is a proclamation, so the company adds it.
        Map<LocalDate, String> h = SaPublicHolidays.of(2022);
        assertEquals("Christmas Day", h.get(d("2022-12-25")));
        assertEquals("Day of Goodwill", h.get(d("2022-12-26")));
        assertNull(h.get(d("2022-12-27")));
    }

    @Test
    void ordinaryDaysAreNotHolidays() {
        assertNull(SaPublicHolidays.nameOn(d("2026-11-23")));
        assertNull(SaPublicHolidays.nameOn(d("2026-07-04")));
        assertNull(SaPublicHolidays.nameOn(d("2026-04-04")));
    }

    @Test
    void fixedDatesAreAllThere() {
        Map<LocalDate, String> h = SaPublicHolidays.of(2027);
        for (String s : new String[]{"2027-01-01", "2027-03-21", "2027-04-27", "2027-05-01", "2027-06-16", "2027-08-09", "2027-09-24", "2027-12-16", "2027-12-25", "2027-12-26"})
            assertNotNull(h.get(d(s)), s);
    }

    @Test
    void listIsInDateOrder() {
        LocalDate prev = LocalDate.MIN;
        for (LocalDate x : SaPublicHolidays.of(2026).keySet()) { assertTrue(x.isAfter(prev)); prev = x; }
    }
}
