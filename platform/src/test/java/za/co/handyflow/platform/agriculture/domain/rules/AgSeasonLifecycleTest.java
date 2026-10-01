package za.co.handyflow.platform.agriculture.domain.rules;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class AgSeasonLifecycleTest {

    @Test
    @DisplayName("a planning or closed season can be activated; an active one cannot be activated again")
    void activate() {
        assertDoesNotThrow(() -> AgSeasonLifecycle.requireCanActivate("PLANNING"));
        assertDoesNotThrow(() -> AgSeasonLifecycle.requireCanActivate("CLOSED"));   // reopening is allowed
        assertEquals("season is already active", assertThrows(IllegalStateException.class, () -> AgSeasonLifecycle.requireCanActivate("ACTIVE")).getMessage());
    }

    @Test
    @DisplayName("only an active season can be closed")
    void close() {
        assertDoesNotThrow(() -> AgSeasonLifecycle.requireCanClose("ACTIVE"));
        IllegalStateException planning = assertThrows(IllegalStateException.class, () -> AgSeasonLifecycle.requireCanClose("PLANNING"));
        assertTrue(planning.getMessage().contains("PLANNING"));
        assertThrows(IllegalStateException.class, () -> AgSeasonLifecycle.requireCanClose("CLOSED"));
    }

    @Test
    @DisplayName("crop cycles cannot be added to a closed season")
    void closedSeasonRefusesCycles() {
        assertDoesNotThrow(() -> AgSeasonLifecycle.requireOpenForNewCycles("PLANNING"));
        assertDoesNotThrow(() -> AgSeasonLifecycle.requireOpenForNewCycles("ACTIVE"));
        assertThrows(IllegalStateException.class, () -> AgSeasonLifecycle.requireOpenForNewCycles("CLOSED"));
    }

    @Test
    @DisplayName("end date cannot be before start date; an open-ended season is fine")
    void dates() {
        LocalDate start = LocalDate.of(2026, 10, 1);
        assertDoesNotThrow(() -> AgSeasonLifecycle.requireValidDates(start, null));
        assertDoesNotThrow(() -> AgSeasonLifecycle.requireValidDates(start, start));
        assertDoesNotThrow(() -> AgSeasonLifecycle.requireValidDates(start, start.plusMonths(6)));
        assertThrows(IllegalArgumentException.class, () -> AgSeasonLifecycle.requireValidDates(start, start.minusDays(1)));
    }
}
