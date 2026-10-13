package za.co.handyflow.platform.debtcollection.application.internal;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BusinessDaysTest {

    private static final LocalDate FRIDAY = LocalDate.of(2026, 10, 9);

    @Test
    void zeroDaysReturnsSameDate() {
        assertEquals(FRIDAY, BusinessDays.addBusinessDays(FRIDAY, 0, Set.of()));
    }

    @Test
    void skipsWeekends() {
        assertEquals(LocalDate.of(2026, 10, 12), BusinessDays.addBusinessDays(FRIDAY, 1, Set.of()));
        assertEquals(LocalDate.of(2026, 10, 23), BusinessDays.addBusinessDays(FRIDAY, 10, Set.of()));
    }

    @Test
    void skipsConfiguredPublicHolidays() {
        Set<LocalDate> holidays = Set.of(LocalDate.of(2026, 10, 14));
        assertEquals(LocalDate.of(2026, 10, 26), BusinessDays.addBusinessDays(FRIDAY, 10, holidays));
        assertFalse(BusinessDays.isBusinessDay(LocalDate.of(2026, 10, 14), holidays));
        assertTrue(BusinessDays.isBusinessDay(LocalDate.of(2026, 10, 15), holidays));
    }

    @Test
    void negativeDaysRejected() {
        assertThrows(IllegalArgumentException.class, () -> BusinessDays.addBusinessDays(FRIDAY, -1, Set.of()));
    }
}
