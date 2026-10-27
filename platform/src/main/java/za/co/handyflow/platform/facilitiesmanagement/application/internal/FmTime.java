package za.co.handyflow.platform.facilitiesmanagement.application.internal;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Facilities management is a South African, calendar-day business: due dates, invoice dates, ledger dates and
 * PPM dates are all "what day is it in South Africa". The server's own time zone (often UTC in the cloud) must
 * never decide that, or work done after 22:00 SAST lands on the wrong day.
 */
public final class FmTime {

    public static final ZoneId ZONE = ZoneId.of("Africa/Johannesburg");

    private FmTime() {}

    /** Today's date in South Africa. */
    public static LocalDate today() {
        return LocalDate.now(ZONE);
    }

    /** The South African calendar date an instant falls on. */
    public static LocalDate toLocalDate(Instant instant) {
        return instant.atZone(ZONE).toLocalDate();
    }
}
