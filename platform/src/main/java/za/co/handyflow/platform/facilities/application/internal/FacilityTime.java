package za.co.handyflow.platform.facilities.application.internal;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * The calendar this module runs on. "Today", overdue and expiry are South African calendar days, never the server's
 * zone (which in the cloud is UTC and would flip the date two hours early). Mirrors the FM module's own helper;
 * the modules may not depend on each other.
 */
public final class FacilityTime {

    public static final ZoneId ZONE = ZoneId.of("Africa/Johannesburg");

    private FacilityTime() {}

    public static LocalDate today() { return LocalDate.now(ZONE); }

    public static LocalDate toLocalDate(Instant instant) { return instant.atZone(ZONE).toLocalDate(); }
}
