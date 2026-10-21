package za.co.handyflow.platform.fleet.application.internal;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FuelLogFilterTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 21);

    @Test
    void defaultsToTheLastThirtyDays() {
        var p = FuelLogFilter.period(null, null, TODAY);
        assertEquals(TODAY, p.to());
        assertEquals(LocalDate.of(2026, 9, 22), p.from());
    }

    @Test
    void keepsAGivenPeriod() {
        var p = FuelLogFilter.period(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31), TODAY);
        assertEquals(LocalDate.of(2026, 1, 1), p.from());
        assertEquals(LocalDate.of(2026, 3, 31), p.to());
    }

    @Test
    void refusesBackwardsAndOverlongPeriods() {
        assertThrows(IllegalArgumentException.class, () -> FuelLogFilter.period(LocalDate.of(2026, 5, 2), LocalDate.of(2026, 5, 1), TODAY));
        assertThrows(IllegalArgumentException.class, () -> FuelLogFilter.period(LocalDate.of(2025, 1, 1), LocalDate.of(2026, 10, 1), TODAY));
    }

    @Test
    void searchTextIsMatchedLiterally() {
        assertEquals("%", FuelLogFilter.pattern(null));
        assertEquals("%", FuelLogFilter.pattern("   "));
        assertEquals("%shell%", FuelLogFilter.pattern(" Shell "));
        assertEquals("%100\\%\\_x%", FuelLogFilter.pattern("100%_x"));
    }
}
