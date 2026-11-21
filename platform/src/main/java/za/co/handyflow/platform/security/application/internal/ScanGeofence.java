package za.co.handyflow.platform.security.application.internal;

import java.math.BigDecimal;

/**
 * Is a checkpoint scan being made at the site? Pure.
 * Uses the same radius and accuracy rules as clock-in ({@link SessionGeofence}).
 * Unknown (no site position, poor fix) is allowed, because it says nothing against the guard.
 * A missing position from the phone is only refused when the platform is set to require GPS.
 */
final class ScanGeofence {

    enum Verdict { OK, TOO_FAR, GPS_REQUIRED }

    private ScanGeofence() {}

    static Verdict decide(BigDecimal siteLat, BigDecimal siteLon,
                          BigDecimal lat, BigDecimal lon, Double accuracyMetres, boolean requireGps) {
        if (siteLat == null || siteLon == null) return Verdict.OK; // nothing to compare against
        if (lat == null || lon == null) return requireGps ? Verdict.GPS_REQUIRED : Verdict.OK;
        Boolean inside = SessionGeofence.evaluate(siteLat, siteLon, lat, lon, accuracyMetres);
        return Boolean.FALSE.equals(inside) ? Verdict.TOO_FAR : Verdict.OK;
    }
}
