package za.co.handyflow.platform.clinic.application.internal;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReminderRulesTest {

    private static boolean sends(String utc) {
        return ReminderRules.inSendingHours(Instant.parse(utc), ReminderRules.CLINIC_ZONE);
    }

    @Test
    void sendsOnlyBetweenEightAndTwentyHundredClinicTime() {
        assertFalse(sends("2026-10-07T05:59:00Z")); // 07:59 in Johannesburg
        assertTrue(sends("2026-10-07T06:00:00Z"));  // 08:00
        assertTrue(sends("2026-10-07T17:59:00Z"));  // 19:59
        assertFalse(sends("2026-10-07T18:00:00Z")); // 20:00
        assertFalse(sends("2026-10-07T22:30:00Z")); // 00:30
    }

    @Test
    void windowIsTwoToTwentyFourHoursAhead() {
        Instant now = Instant.parse("2026-10-07T10:00:00Z");
        assertEquals(Duration.ofHours(2), Duration.between(now, ReminderRules.from(now)));
        assertEquals(Duration.ofHours(24), Duration.between(now, ReminderRules.to(now)));
    }
}
