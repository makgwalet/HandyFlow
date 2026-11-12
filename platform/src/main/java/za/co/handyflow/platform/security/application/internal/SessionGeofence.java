// security/application/internal/SessionGeofence.java
package za.co.handyflow.platform.security.application.internal;

import java.math.BigDecimal;

/**
 * Server-side "is the guard on site" decision for clock-in. Pure, so it is easy to test.
 *
 * Returns null (unknown) rather than false when it cannot decide: the site has no saved
 * position, the phone sent none, or the GPS fix is too poor to mean anything. Unknown is
 * recorded as unknown; only a good fix that is clearly away from the site is false.
 */
public final class SessionGeofence {

    /** Default radius around the site's saved position. A per-site radius needs a column; later. */
    public static final double DEFAULT_RADIUS_METRES = 300;
    /** A fix worse than this says nothing useful about where the guard is. */
    public static final double MAX_USABLE_ACCURACY_METRES = 150;
    /** Credit given for GPS error, capped so a phone cannot claim a huge error to pass. */
    public static final double MAX_ACCURACY_CREDIT_METRES = 50;

    private SessionGeofence() {}

    public static Boolean evaluate(BigDecimal siteLat, BigDecimal siteLon,
                                   BigDecimal lat, BigDecimal lon, Double accuracyMetres) {
        if (siteLat == null || siteLon == null || lat == null || lon == null) return null;
        if (accuracyMetres != null && (accuracyMetres < 0 || accuracyMetres > MAX_USABLE_ACCURACY_METRES)) {
            return null;
        }
        double credit = accuracyMetres == null ? 0 : Math.min(accuracyMetres, MAX_ACCURACY_CREDIT_METRES);
        double distance = CheckpointScanService.haversineMetres(siteLat, siteLon, lat, lon);
        return distance <= DEFAULT_RADIUS_METRES + credit;
    }
}
