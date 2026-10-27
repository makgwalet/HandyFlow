package za.co.handyflow.platform.facilities.application.internal;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;

class FacilityOverduePolicyTest {

    @Test
    void alertsOnTheFirstDaysThenBacksOff() {
        for (long d : new long[]{1, 3, 7, 14, 30, 60, 90}) assertTrue(FacilityOverduePolicy.shouldAlert(d), "day " + d);
        for (long d : new long[]{2, 4, 6, 8, 13, 15, 29, 31, 45}) assertFalse(FacilityOverduePolicy.shouldAlert(d), "day " + d);
    }

    @Test
    void neverAlertsBeforeItIsOverdue() {
        assertFalse(FacilityOverduePolicy.shouldAlert(0));
        assertFalse(FacilityOverduePolicy.shouldAlert(-5));
    }

    @Test
    void facilityDatesUseSouthAfricanTime() {
        // 23:30 UTC on 1 Jan is already 01:30 on 2 Jan in Johannesburg
        assertEquals(LocalDate.of(2027, 1, 2), FacilityTime.toLocalDate(Instant.parse("2027-01-01T23:30:00Z")));
        assertEquals(LocalDate.of(2027, 1, 1), FacilityTime.toLocalDate(Instant.parse("2027-01-01T21:30:00Z")));
    }
}
