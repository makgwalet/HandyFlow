package za.co.handyflow.platform.clinic.application.internal;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DashboardRulesTest {

    private static final ZoneId SAST = DashboardRules.CLINIC_ZONE;

    @Test
    void theClinicDayStartsAtMidnightSouthAfricanTime() {
        Instant now = Instant.parse("2026-10-07T10:00:00Z");
        assertEquals(Instant.parse("2026-10-06T22:00:00Z"), DashboardRules.dayStart(now, SAST));
        assertEquals(Instant.parse("2026-10-07T22:00:00Z"), DashboardRules.dayEnd(now, SAST));
    }

    @Test
    void justAfterMidnightInSouthAfricaIsAlreadyTheNextDay() {
        Instant now = Instant.parse("2026-10-07T22:30:00Z");
        assertEquals(LocalDate.of(2026, 10, 8), DashboardRules.localDate(now, SAST));
    }

    @Test
    void lateEveningStillBelongsToTheSameDay() {
        Instant now = Instant.parse("2026-10-07T21:59:59Z");
        assertEquals(LocalDate.of(2026, 10, 7), DashboardRules.localDate(now, SAST));
    }

    @Test
    void aDayIsTwentyFourHoursInSouthAfrica() {
        Instant now = Instant.parse("2026-03-29T10:00:00Z");
        assertEquals(Duration.ofHours(24), Duration.between(DashboardRules.dayStart(now, SAST), DashboardRules.dayEnd(now, SAST)));
    }

    @Test
    void daysFollowTheZoneWhenClocksChange() {
        ZoneId london = ZoneId.of("Europe/London");
        Instant now = Instant.parse("2026-03-29T12:00:00Z");
        assertEquals(Duration.ofHours(23), Duration.between(DashboardRules.dayStart(now, london), DashboardRules.dayEnd(now, london)));
    }

    @Test
    void awaitingCountsEveryoneBookedOrArrivedButNotYetSeen() {
        Map<String, Integer> by = Map.of("SCHEDULED", 3, "CONFIRMED", 2, "CHECKED_IN", 1, "TRIAGED", 1,
                "IN_PROGRESS", 1, "COMPLETED", 4, "CANCELLED", 2, "NO_SHOW", 1);
        assertEquals(7, DashboardRules.awaiting(by));
        assertEquals(15, DashboardRules.total(by));
        assertEquals(4, DashboardRules.count(by, "COMPLETED"));
    }

    @Test
    void missingStatusesCountAsZero() {
        assertEquals(0, DashboardRules.awaiting(Map.of()));
        assertEquals(0, DashboardRules.total(Map.of()));
    }
}
