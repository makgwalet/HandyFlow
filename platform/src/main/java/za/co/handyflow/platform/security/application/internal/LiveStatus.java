// security/application/internal/LiveStatus.java
package za.co.handyflow.platform.security.application.internal;

import java.time.Duration;
import java.time.Instant;

/**
 * How live a guard on shift is, from the last GPS ping. Pure.
 * LIVE: pinged within the liveness threshold. STALE: has pinged, but not recently. NO_GPS: no ping since the shift
 * started (a ping from an earlier shift does not count, it says nothing about where the guard is now).
 */
public final class LiveStatus {

    private LiveStatus() {}

    public static final String LIVE = "LIVE", STALE = "STALE", NO_GPS = "NO_GPS";

    public static String gps(Instant recordedAt, Instant shiftStart, Instant now) {
        if (recordedAt == null || (shiftStart != null && recordedAt.isBefore(shiftStart))) return NO_GPS;
        return recordedAt.isBefore(now.minus(Duration.ofMinutes(GuardLocationService.LIVENESS_THRESHOLD_MINUTES))) ? STALE : LIVE;
    }

    /** A shift still marked active after its planned end: the guard may not have been relieved or clocked out. */
    public static boolean overrunning(Instant shiftEnd, Instant now) { return shiftEnd != null && now.isAfter(shiftEnd); }
}
