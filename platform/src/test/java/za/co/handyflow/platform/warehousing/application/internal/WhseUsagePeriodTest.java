package za.co.handyflow.platform.warehousing.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class WhseUsagePeriodTest {

    private static WhseUsagePeriod.Period p(LocalDate today, Integer anchor) { return WhseUsagePeriod.containing(today, anchor); }

    @Test @DisplayName("anchor 1 or none is the calendar month")
    void calendar() {
        assertEquals(new WhseUsagePeriod.Period(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 12, 1)), p(LocalDate.of(2026, 11, 11), 1));
        assertEquals(p(LocalDate.of(2026, 11, 11), 1), p(LocalDate.of(2026, 11, 11), null));
        assertEquals(p(LocalDate.of(2026, 11, 11), 1), p(LocalDate.of(2026, 11, 11), 0)); // nonsense falls back to the 1st
        assertEquals(p(LocalDate.of(2026, 11, 11), 1), p(LocalDate.of(2026, 11, 11), 45));
    }

    @Test @DisplayName("a mid-month anchor: before it the period began last month, on or after it the period began this month")
    void midMonth() {
        assertEquals(new WhseUsagePeriod.Period(LocalDate.of(2026, 10, 15), LocalDate.of(2026, 11, 15)), p(LocalDate.of(2026, 11, 14), 15));
        assertEquals(new WhseUsagePeriod.Period(LocalDate.of(2026, 11, 15), LocalDate.of(2026, 12, 15)), p(LocalDate.of(2026, 11, 15), 15));
    }

    @Test @DisplayName("the last day before renewal still belongs to the old period, the renewal day to the new")
    void boundary() {
        assertEquals(LocalDate.of(2026, 11, 15), p(LocalDate.of(2026, 11, 14), 15).endExclusive());
        assertEquals(LocalDate.of(2026, 11, 15), p(LocalDate.of(2026, 11, 15), 15).start());
    }

    @Test @DisplayName("an anchor past a short month's end renews on its last day")
    void shortMonth() {
        assertEquals(new WhseUsagePeriod.Period(LocalDate.of(2027, 2, 28), LocalDate.of(2027, 3, 31)), p(LocalDate.of(2027, 3, 10), 31));
        assertEquals(new WhseUsagePeriod.Period(LocalDate.of(2027, 1, 31), LocalDate.of(2027, 2, 28)), p(LocalDate.of(2027, 2, 27), 31));
        assertEquals(new WhseUsagePeriod.Period(LocalDate.of(2028, 2, 29), LocalDate.of(2028, 3, 30)), p(LocalDate.of(2028, 3, 1), 30)); // leap year
    }

    @Test @DisplayName("across a year end")
    void yearEnd() {
        assertEquals(new WhseUsagePeriod.Period(LocalDate.of(2026, 12, 20), LocalDate.of(2027, 1, 20)), p(LocalDate.of(2027, 1, 5), 20));
    }
}
