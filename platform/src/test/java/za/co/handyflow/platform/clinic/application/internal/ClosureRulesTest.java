package za.co.handyflow.platform.clinic.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.clinic.application.internal.ClosureRules.Closure;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ClosureRulesTest {

    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");
    private static final LocalDate D = LocalDate.of(2026, 12, 25);

    @Test
    void acceptsOneDayAndTrimsTheReason() {
        assertEquals("Christmas Day", ClosureRules.validate(D, D, "  Christmas Day "));
        assertNull(ClosureRules.validate(D, D, "  "));
        assertNull(ClosureRules.validate(D, D, null));
    }

    @Test
    void refusesClosuresThatMakeNoSense() {
        assertThrows(IllegalArgumentException.class, () -> ClosureRules.validate(null, D, null));
        assertThrows(IllegalArgumentException.class, () -> ClosureRules.validate(D, null, null));
        assertThrows(IllegalArgumentException.class, () -> ClosureRules.validate(D, D.minusDays(1), null));
        assertThrows(IllegalArgumentException.class, () -> ClosureRules.validate(D, D.plusDays(60), null));   // 61 days
        assertThrows(IllegalArgumentException.class, () -> ClosureRules.validate(D, D, "x".repeat(201)));
        assertEquals(null, ClosureRules.validate(D, D.plusDays(59), null));                                    // exactly 60 days
    }

    @Test
    void daysTouchedUseClinicTimeNotUtc() {
        // 23:30 Johannesburg on 24 Dec is 21:30Z on 24 Dec; 00:30 on 25 Dec is 22:30Z on 24 Dec
        LocalDate[] late = ClosureRules.daysTouched(Instant.parse("2026-12-24T22:30:00Z"), 30, SAST);
        assertEquals(D, late[0]);
        assertEquals(D, late[1]);
    }

    @Test
    void aBookingRunningPastMidnightTouchesBothDays() {
        // 23:45 to 00:15
        LocalDate[] days = ClosureRules.daysTouched(Instant.parse("2026-12-24T21:45:00Z"), 30, SAST);
        assertEquals(LocalDate.of(2026, 12, 24), days[0]);
        assertEquals(D, days[1]);
    }

    @Test
    void aBookingEndingExactlyAtMidnightDoesNotTouchTheNextDay() {
        // 23:30 to 00:00
        LocalDate[] days = ClosureRules.daysTouched(Instant.parse("2026-12-24T21:30:00Z"), 30, SAST);
        assertEquals(LocalDate.of(2026, 12, 24), days[0]);
        assertEquals(LocalDate.of(2026, 12, 24), days[1]);
    }

    @Test
    void messageNamesTheDaysAndReason() {
        assertEquals("The clinic is closed Fri 25 Dec (Christmas Day).",
                ClosureRules.message(List.of(new Closure(D, D, "Christmas Day")), SAST));
        assertEquals("The clinic is closed Fri 25 Dec to Sat 26 Dec and 1 more closure(s).",
                ClosureRules.message(List.of(new Closure(D, D.plusDays(1), null), new Closure(D.plusDays(5), D.plusDays(5), null)), SAST));
    }
}
