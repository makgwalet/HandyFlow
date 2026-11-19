package za.co.handyflow.platform.marketing.application.internal;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The send-window rule for journeys: emails only go out inside the tenant's SAST window, and (optionally) not at weekends.
 * 2026-11-20 is a Friday, 2026-11-21 a Saturday, 2026-11-23 a Monday. South Africa has no daylight saving (UTC+2).
 */
class MarketingJourneyEngineTest {

    static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");

    private static Instant at(int day, int hour) { return ZonedDateTime.of(2026, 11, day, hour, 0, 0, 0, SAST).toInstant(); }

    @Test void inside_the_window_sends_now() {
        assertEquals(at(20, 12), MarketingJourneyEngine.nextAllowed(at(20, 12), 8, 17, true));
    }

    @Test void before_the_window_waits_until_it_opens_the_same_day() {
        assertEquals(at(20, 8), MarketingJourneyEngine.nextAllowed(at(20, 6), 8, 17, true));
    }

    @Test void after_the_window_waits_until_the_next_working_morning() {
        assertEquals(at(23, 8), MarketingJourneyEngine.nextAllowed(at(20, 18), 8, 17, true), "Friday evening -> Monday 08:00");
    }

    @Test void the_window_end_is_exclusive() {
        assertEquals(at(23, 8), MarketingJourneyEngine.nextAllowed(at(20, 17), 8, 17, true));
    }

    @Test void a_weekend_is_skipped_when_asked() {
        assertEquals(at(23, 8), MarketingJourneyEngine.nextAllowed(at(21, 12), 8, 17, true));
    }

    @Test void a_weekend_is_allowed_when_not_skipped() {
        assertEquals(at(21, 12), MarketingJourneyEngine.nextAllowed(at(21, 12), 8, 17, false));
    }

    @Test void weekday_after_hours_without_weekend_skip_goes_to_next_morning() {
        assertEquals(at(21, 8), MarketingJourneyEngine.nextAllowed(at(20, 18), 8, 17, false), "Friday evening -> Saturday 08:00");
    }
}
