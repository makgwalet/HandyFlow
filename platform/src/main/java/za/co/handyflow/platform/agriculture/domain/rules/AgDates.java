package za.co.handyflow.platform.agriculture.domain.rules;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * "Today" for a South African farm. The server runs in UTC, so {@code LocalDate.now()} is still yesterday between 00:00 and 02:00
 * SAST: a record dated today would be refused as "in the future" and stock movements would carry the wrong date.
 */
public final class AgDates {

    public static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");

    private AgDates() {}

    public static LocalDate today() { return today(Clock.systemUTC()); }

    public static LocalDate today(Clock clock) { return LocalDate.now(clock.withZone(SAST)); }
}
