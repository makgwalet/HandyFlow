package za.co.handyflow.platform.earthmoving.domain.model;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;

/**
 * What may be captured in the field, and how a late entry is judged when it reaches the server. The phone is trusted to remember what the person typed
 * and when; it is not trusted to decide anything. Every entry is re-checked here against the machine as it is now.
 */
public final class FieldRules {
    private FieldRules() {}

    public static final String METER_READING = "METER_READING", POSITION_READING = "POSITION_READING";
    /** Entries that may be made offline and checked when they arrive. */
    public static final Set<String> OFFLINE_KINDS = Set.of(METER_READING, POSITION_READING);
    /** Actions that need a fresh check against the live record, so they are never queued: they must be done while connected. */
    public static final Set<String> ONLINE_ONLY_KINDS = Set.of("DEPLOYMENT", "MAINTENANCE_RELEASE", "DISPOSAL_APPROVAL");

    public static final String SYNCED = "SYNCED", REJECTED = "REJECTED", CONFLICT = "CONFLICT";
    public static final String KEPT_SERVER = "KEPT_SERVER", APPLIED = "APPLIED";

    /** An entry may be captured this long ahead of the server clock (phone clock drift). */
    public static final Duration CLOCK_DRIFT = Duration.ofMinutes(5);
    public static final int MAX_BATCH = 100;

    public static String kind(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    /** Null when the kind may be handled; otherwise the reason it is refused. */
    public static String kindProblem(String kind) {
        if (OFFLINE_KINDS.contains(kind)) return null;
        if (ONLINE_ONLY_KINDS.contains(kind))
            return "This needs a fresh check against the live record, so it cannot be saved offline and sent later. Do it while connected.";
        return "That kind of entry is not accepted from the field.";
    }

    /** Null when the entry is acceptable; otherwise what is wrong with it. */
    public static String entryProblem(String kind, BigDecimal meter, BigDecimal latitude, BigDecimal longitude, Instant capturedAt, Instant now) {
        if (capturedAt == null) return "The entry has no capture time.";
        if (capturedAt.isAfter(now.plus(CLOCK_DRIFT))) return "The capture time is in the future. Check the phone's clock.";
        if (METER_READING.equals(kind)) {
            if (meter == null) return "A meter reading needs a value.";
            if (meter.signum() < 0) return "A meter reading cannot be negative.";
        }
        if (POSITION_READING.equals(kind)) {
            if (latitude == null || longitude == null) return "A position needs both latitude and longitude.";
            if (latitude.abs().compareTo(BigDecimal.valueOf(90)) > 0 || longitude.abs().compareTo(BigDecimal.valueOf(180)) > 0) return "The position is out of range.";
        }
        return null;
    }

    /**
     * Judges a meter reading against the machine's latest reading on the server. An entry captured before the server's latest reading must not overwrite it,
     * so it is a conflict and both versions are kept. Otherwise a reading below the machine's current one is refused (a correction is the way to lower it).
     *
     * @return null when it can be applied, or a Verdict explaining why not
     */
    public static Verdict judgeMeter(BigDecimal value, Instant capturedAt, BigDecimal currentMeter, Instant latestRecordedAt) {
        if (latestRecordedAt != null && capturedAt.isBefore(latestRecordedAt))
            return new Verdict(CONFLICT, "A newer reading was recorded on the server after this one was taken. Both are kept for review.");
        if (currentMeter != null && value.compareTo(currentMeter) < 0)
            return new Verdict(REJECTED, "The reading is below the machine's current meter (" + currentMeter.stripTrailingZeros().toPlainString() + "). A wrong reading is fixed with a correction, not a new reading.");
        return null;
    }

    public record Verdict(String status, String reason) {}

    public static String decision(String value) {
        String v = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!KEPT_SERVER.equals(v) && !APPLIED.equals(v)) throw new IllegalArgumentException("The decision is KEPT_SERVER or APPLIED.");
        return v;
    }

    public static String note(String value, int max) {
        String v = value == null ? "" : value.trim();
        if (v.length() > max) throw new IllegalArgumentException("Keep the note under " + max + " characters.");
        return v.isEmpty() ? null : v;
    }
}
