package za.co.handyflow.platform.facilities.application.internal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FacilityLicenceRulesTest {

    @Test
    void remindsOnTheMilestoneDaysBeforeExpiry() {
        for (long d : new long[]{30, 14, 7, 3, 1, 0}) assertTrue(FacilityLicenceRules.shouldRemind(d), "day " + d);
    }

    @Test
    void remindsOnceTheDayAfterItLapses() {
        assertTrue(FacilityLicenceRules.shouldRemind(-1));
    }

    @Test
    void staysQuietOnOtherDays() {
        for (long d : new long[]{31, 29, 20, 10, 5, 2, -2, -10, -90}) assertFalse(FacilityLicenceRules.shouldRemind(d), "day " + d);
    }
}
