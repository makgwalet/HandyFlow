package za.co.handyflow.platform.facilitiesmanagement.application.internal;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class FmTimeTest {

    @Test
    void anInstantLateInTheEveningUtcIsAlreadyTomorrowInSouthAfrica() {
        assertEquals(LocalDate.of(2026, 10, 28), FmTime.toLocalDate(Instant.parse("2026-10-27T22:30:00Z")));
    }

    @Test
    void anInstantJustBeforeMidnightSouthAfricanTimeIsStillToday() {
        assertEquals(LocalDate.of(2026, 10, 27), FmTime.toLocalDate(Instant.parse("2026-10-27T21:59:59Z")));
    }

    @Test
    void southAfricaDoesNotObserveDaylightSaving() {
        assertEquals(LocalDate.of(2026, 1, 1), FmTime.toLocalDate(Instant.parse("2025-12-31T22:00:00Z")));
        assertEquals(LocalDate.of(2026, 7, 1), FmTime.toLocalDate(Instant.parse("2026-06-30T22:00:00Z")));
    }

    @Test
    void todayIsInTheSouthAfricanZone() {
        assertEquals(LocalDate.now(FmTime.ZONE), FmTime.today());
    }
}
