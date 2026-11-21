package za.co.handyflow.platform.security.application.internal;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static za.co.handyflow.platform.security.application.internal.ScanGeofence.Verdict.*;

class ScanGeofenceTest {
    private static final BigDecimal SITE_LAT = new BigDecimal("-26.1076"), SITE_LON = new BigDecimal("28.0567"); // Sandton
    private static BigDecimal d(String v) { return new BigDecimal(v); }

    @Test void atTheSiteIsOk() {
        assertEquals(OK, ScanGeofence.decide(SITE_LAT, SITE_LON, d("-26.1077"), d("28.0568"), 10.0, false));
    }
    @Test void photographedQrFromAnotherCityIsRefused() {
        assertEquals(TOO_FAR, ScanGeofence.decide(SITE_LAT, SITE_LON, d("-33.9249"), d("18.4241"), 10.0, false));
    }
    @Test void poorFixIsNotHeldAgainstTheGuard() {
        assertEquals(OK, ScanGeofence.decide(SITE_LAT, SITE_LON, d("-33.9249"), d("18.4241"), 500.0, false));
    }
    @Test void siteWithoutPositionIsOk() {
        assertEquals(OK, ScanGeofence.decide(null, null, d("-33.9249"), d("18.4241"), 10.0, true));
    }
    @Test void missingPositionOkUnlessRequired() {
        assertEquals(OK, ScanGeofence.decide(SITE_LAT, SITE_LON, null, null, null, false));
        assertEquals(GPS_REQUIRED, ScanGeofence.decide(SITE_LAT, SITE_LON, null, null, null, true));
    }
}
