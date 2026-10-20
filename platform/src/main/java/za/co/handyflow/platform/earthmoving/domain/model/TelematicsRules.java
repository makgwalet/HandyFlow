package za.co.handyflow.platform.earthmoving.domain.model;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

/**
 * Provider-neutral telematics rules. A reading is "last seen", never "live": how fresh it is decides how it is described. Provider readings and manual
 * readings are separate sources, and neither overwrites the machine's own meter.
 */
public final class TelematicsRules {
    private TelematicsRules() {}

    public static final String PROVIDER = "PROVIDER", MANUAL = "MANUAL";
    public static final String HOURS = "HOURS", KILOMETRES = "KILOMETRES";

    public enum Freshness { NEVER, FRESH, STALE, OFFLINE }
    /** Seen within this long: fresh. Within a day: stale. Longer: offline. */
    public static final Duration FRESH_FOR = Duration.ofMinutes(30);
    public static final Duration STALE_FOR = Duration.ofHours(24);
    /** A reading may be this far ahead of our clock (device clock drift) and no further. */
    public static final Duration CLOCK_DRIFT = Duration.ofMinutes(5);
    /** A reading older than this is refused: it describes a time nobody is acting on. */
    public static final Duration MAX_AGE = Duration.ofDays(30);

    public static Freshness freshness(Instant lastSeen, Instant now) {
        if (lastSeen == null) return Freshness.NEVER;
        Duration age = Duration.between(lastSeen, now);
        if (age.compareTo(FRESH_FOR) <= 0) return Freshness.FRESH;
        return age.compareTo(STALE_FOR) <= 0 ? Freshness.STALE : Freshness.OFFLINE;
    }

    public static String providerCode(String value) {
        String v = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!v.matches("[A-Z0-9_]{2,40}")) throw new IllegalArgumentException("The provider code is 2 to 40 letters, digits or underscores.");
        return v;
    }

    public static String externalId(String value) {
        String v = value == null ? "" : value.trim();
        if (v.isEmpty() || v.length() > 120) throw new IllegalArgumentException("Give the device's id, up to 120 characters.");
        return v;
    }

    public static String unit(String value) {
        String v = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!HOURS.equals(v) && !KILOMETRES.equals(v)) throw new IllegalArgumentException("The meter unit is HOURS or KILOMETRES.");
        return v;
    }

    /** The unit a machine's own meter counts in, as a telematics unit. */
    public static String unitForMeter(String meterType) {
        return MeterRules.HOURS.equals(meterType) ? HOURS : KILOMETRES;
    }

    /** Checks a position and/or meter value. At least one must be there; a position needs both coordinates in range. */
    public static void checkReading(BigDecimal latitude, BigDecimal longitude, BigDecimal meter, String unit) {
        if ((latitude == null) != (longitude == null)) throw new IllegalArgumentException("A position needs both latitude and longitude.");
        if (latitude == null && meter == null) throw new IllegalArgumentException("A reading needs a position, a meter value, or both.");
        if (latitude != null) {
            if (latitude.compareTo(BigDecimal.valueOf(-90)) < 0 || latitude.compareTo(BigDecimal.valueOf(90)) > 0) throw new IllegalArgumentException("Latitude must be between -90 and 90.");
            if (longitude.compareTo(BigDecimal.valueOf(-180)) < 0 || longitude.compareTo(BigDecimal.valueOf(180)) > 0) throw new IllegalArgumentException("Longitude must be between -180 and 180.");
        }
        if (meter != null) {
            if (meter.signum() < 0) throw new IllegalArgumentException("A meter value cannot be negative.");
            unit(unit);
        }
    }

    public static void checkTime(Instant recordedAt, Instant now) {
        if (recordedAt == null) throw new IllegalArgumentException("A reading needs the time it was taken.");
        if (recordedAt.isAfter(now.plus(CLOCK_DRIFT))) throw new IllegalArgumentException("The reading is dated in the future.");
        if (recordedAt.isBefore(now.minus(MAX_AGE))) throw new IllegalArgumentException("The reading is more than 30 days old.");
    }

    public static String reason(String value) {
        String v = value == null ? "" : value.trim();
        if (v.length() < 5) throw new IllegalArgumentException("Give a reason of at least 5 characters.");
        if (v.length() > 1000) throw new IllegalArgumentException("Keep the reason under 1000 characters.");
        return v;
    }

    /** Whether a reading is newer than the one we already show. Ordered by when it was taken, never by when it arrived. */
    public static boolean isNewer(Instant candidate, Instant current) {
        return current == null || candidate.isAfter(current);
    }

    /**
     * How the provider's meter compares with the machine's own, in the machine's unit. Null when either is missing or the units differ.
     * Positive means the provider reads higher. Reported only; nothing is changed.
     */
    public static BigDecimal meterDifference(BigDecimal providerMeter, String providerUnit, BigDecimal machineMeter, String machineUnit) {
        if (providerMeter == null || machineMeter == null || providerUnit == null || !providerUnit.equals(machineUnit)) return null;
        return providerMeter.subtract(machineMeter);
    }
}
