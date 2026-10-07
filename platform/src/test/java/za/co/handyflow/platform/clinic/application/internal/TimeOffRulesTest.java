package za.co.handyflow.platform.clinic.application.internal;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TimeOffRulesTest {

    private static final Instant NINE = Instant.parse("2026-10-07T07:00:00Z");  // 09:00 Johannesburg
    private static final Instant FIVE = Instant.parse("2026-10-07T15:00:00Z");  // 17:00

    @Test
    void acceptsAnOrdinaryBlockAndTrimsTheReason() {
        assertEquals("Annual leave", TimeOffRules.validate(NINE, FIVE, "  Annual leave "));
        assertNull(TimeOffRules.validate(NINE, FIVE, "   "));
        assertNull(TimeOffRules.validate(NINE, FIVE, null));
    }

    @Test
    void refusesBlocksThatMakeNoSense() {
        assertThrows(IllegalArgumentException.class, () -> TimeOffRules.validate(FIVE, NINE, null));
        assertThrows(IllegalArgumentException.class, () -> TimeOffRules.validate(NINE, NINE, null));
        assertThrows(IllegalArgumentException.class, () -> TimeOffRules.validate(null, FIVE, null));
        assertThrows(IllegalArgumentException.class, () -> TimeOffRules.validate(NINE, NINE.plusSeconds(91L * 86400), null));
        assertThrows(IllegalArgumentException.class, () -> TimeOffRules.validate(NINE, FIVE, "x".repeat(201)));
    }

    @Test
    void ninetyDaysIsTheLongestAllowed() {
        assertDoesNotThrow(() -> TimeOffRules.validate(NINE, NINE.plusSeconds(90L * 86400), null));
    }

    @Test
    void overlapIsHalfOpen() {
        assertTrue(TimeOffRules.overlaps(NINE, FIVE, NINE.plusSeconds(60), NINE.plusSeconds(1800)));
        assertFalse(TimeOffRules.overlaps(NINE, FIVE, FIVE, FIVE.plusSeconds(1800)));
        assertFalse(TimeOffRules.overlaps(NINE, FIVE, NINE.minusSeconds(1800), NINE));
    }

    @Test
    void messageNamesWhoIsAwayWhenAndWhy() {
        assertEquals("Dr Lee is away 7 Oct 09:00–17:00 (Annual leave).",
                TimeOffRules.message("Dr Lee", List.of(new TimeOffRules.Block(NINE, FIVE, "Annual leave")), AppointmentRules.CLINIC_ZONE));
        assertEquals("This practitioner is away 7 Oct 09:00 to 9 Oct 17:00 and 1 more block(s).",
                TimeOffRules.message(null, List.of(new TimeOffRules.Block(NINE, FIVE.plusSeconds(2 * 86400), null),
                        new TimeOffRules.Block(NINE, FIVE, null)), AppointmentRules.CLINIC_ZONE));
    }
}
