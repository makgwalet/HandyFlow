package za.co.handyflow.platform.security.application.internal;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** Clock-in geofence decided by the server: true, false, or unknown, never taken from the phone. */
class SessionGeofenceTest {

    private static final BigDecimal SITE_LAT = new BigDecimal("-26.2041");
    private static final BigDecimal SITE_LON = new BigDecimal("28.0473");

    private static Boolean at(String lat, String lon, Double accuracy) {
        return SessionGeofence.evaluate(SITE_LAT, SITE_LON, new BigDecimal(lat), new BigDecimal(lon), accuracy);
    }

    @Test
    void aGuardAtTheSiteIsInside() {
        assertThat(at("-26.2041", "28.0473", 10.0)).isTrue();
    }

    @Test
    void aGuardAboutOneKilometreAwayIsOutside() {
        // 0.009 degrees of latitude is roughly 1 km
        assertThat(at("-26.2131", "28.0473", 10.0)).isFalse();
    }

    @Test
    void aSmallGpsErrorCreditLetsAnHonestGuardAtTheEdgeThrough() {
        // about 330 m north: outside 300 m, inside 300 m + the 50 m credit
        assertThat(at("-26.2011", "28.0473", 50.0)).isTrue();
        assertThat(at("-26.2011", "28.0473", 0.0)).isFalse();
    }

    @Test
    void theAccuracyCreditIsCappedSoAPhoneCannotClaimItsWayIn() {
        // about 1 km away, claiming a 149 m error: credit is capped at 50 m, so still outside
        assertThat(at("-26.2131", "28.0473", 149.0)).isFalse();
    }

    @Test
    void aVeryPoorFixIsUnknownNotPassedOrFailed() {
        assertThat(at("-26.2131", "28.0473", 500.0)).isNull();
        assertThat(at("-26.2041", "28.0473", 500.0)).isNull();
    }

    @Test
    void aNegativeAccuracyIsUnknown() {
        assertThat(at("-26.2041", "28.0473", -1.0)).isNull();
    }

    @Test
    void unknownWhenThePhoneSentNoPositionOrTheSiteHasNone() {
        assertThat(SessionGeofence.evaluate(SITE_LAT, SITE_LON, null, null, 10.0)).isNull();
        assertThat(SessionGeofence.evaluate(null, null, new BigDecimal("-26.2"), new BigDecimal("28.0"), 10.0)).isNull();
    }

    @Test
    void noAccuracyReportedStillDecidesOnDistance() {
        assertThat(at("-26.2041", "28.0473", null)).isTrue();
        assertThat(at("-26.2131", "28.0473", null)).isFalse();
    }
}
