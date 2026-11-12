// security/application/internal/OfflineTimestamp.java
package za.co.handyflow.platform.security.application.internal;

import org.springframework.http.HttpStatus;
import za.co.handyflow.platform.shared.HandyFlowException;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * What time a record "happened" when the guard app sends it late. The phone reports when it recorded the event; the
 * server accepts that within limits, because a guard's clock can be wrong and a late scan is still a real scan.
 */
public final class OfflineTimestamp {

    /** Clock drift allowed into the future. A later time is replaced by the server's time. */
    static final Duration FUTURE_TOLERANCE = Duration.ofMinutes(5);
    /** How late a record may arrive. Older than this is refused so a phone cannot back-date. */
    static final Duration MAX_AGE = Duration.ofHours(48);
    /** Anything older than this is called out on the record as raised earlier. */
    static final Duration LATE_NOTE_AFTER = Duration.ofMinutes(2);

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'").withZone(ZoneOffset.UTC);

    private OfflineTimestamp() {}

    /** The time to record: the phone's time if it is sensible, else now. Too old is refused. */
    public static Instant accept(Instant recordedAt, Instant now) {
        if (recordedAt == null) return now;
        if (recordedAt.isAfter(now.plus(FUTURE_TOLERANCE))) return now;
        if (recordedAt.isBefore(now.minus(MAX_AGE))) {
            throw new HandyFlowException("This was recorded more than 48 hours ago and can no longer be sent. Tell your supervisor.",
                    HttpStatus.BAD_REQUEST, "RECORDED_AT_TOO_OLD");
        }
        return recordedAt;
    }

    /** A sentence for the record when it was raised earlier than it arrived, else an empty string. */
    public static String lateNote(Instant accepted, Instant now) {
        if (accepted == null || !accepted.isBefore(now.minus(LATE_NOTE_AFTER))) return "";
        return " [Raised on the guard's phone at " + FORMAT.format(accepted) + ", received " + FORMAT.format(now) + ".]";
    }
}
