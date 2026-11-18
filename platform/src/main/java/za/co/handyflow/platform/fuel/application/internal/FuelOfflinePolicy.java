package za.co.handyflow.platform.fuel.application.internal;

import java.time.Duration;
import java.time.Instant;

/**
 * What the server will accept about the time a phone says it captured something while offline. The system times stay the
 * server's own; the captured time is kept beside them, and only to be looked at. Pure, with an explicit clock for boundary tests.
 */
public final class FuelOfflinePolicy {
    private FuelOfflinePolicy() {}

    /** A phone's clock can be a little ahead of ours. */
    public static final Duration FUTURE_SKEW = Duration.ofMinutes(5);
    /** A queued write older than this is refused: the meters, bays and prices it was judged against have moved on. */
    public static final Duration MAX_AGE = Duration.ofHours(72);

    public static void checkCaptured(Instant capturedAt, Instant now) {
        if (capturedAt == null) return;
        if (capturedAt.isAfter(now.plus(FUTURE_SKEW))) throw new IllegalArgumentException("The time recorded on the phone is in the future. Check the phone's clock.");
        if (capturedAt.isBefore(now.minus(MAX_AGE))) throw new IllegalStateException("This was captured more than " + MAX_AGE.toHours()
                + " hours ago, so it was not accepted. Enter it again with today's readings, or ask a supervisor.");
    }
}
