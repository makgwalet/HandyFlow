package za.co.handyflow.platform.trainingprovider.domain.rules;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TimetableRulesTest {

    private static final LocalDate START = LocalDate.of(2027, 2, 1);
    private static final LocalDate DAY = LocalDate.of(2027, 2, 10);
    private static final LocalTime NINE = LocalTime.of(9, 0);
    private static final LocalTime ELEVEN = LocalTime.of(11, 0);
    private static final UUID S1 = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID S2 = UUID.fromString("00000000-0000-0000-0000-0000000000a2");

    @Test
    void overlapIsStrict() {
        assertTrue(TimetableRules.overlaps(NINE, ELEVEN, LocalTime.of(10, 0), LocalTime.of(12, 0)));
        assertTrue(TimetableRules.overlaps(NINE, ELEVEN, LocalTime.of(9, 30), LocalTime.of(10, 0)));
        assertTrue(TimetableRules.overlaps(LocalTime.of(10, 0), LocalTime.of(12, 0), NINE, ELEVEN));
        assertFalse(TimetableRules.overlaps(NINE, ELEVEN, ELEVEN, LocalTime.of(13, 0)));
        assertFalse(TimetableRules.overlaps(ELEVEN, LocalTime.of(13, 0), NINE, ELEVEN));
        assertFalse(TimetableRules.overlaps(NINE, ELEVEN, LocalTime.of(13, 0), LocalTime.of(14, 0)));
    }

    @Test
    void slotShapeAndCohortDates() {
        assertNull(TimetableRules.slotProblem("Module 1", DAY, NINE, ELEVEN, "PLANNED", START, null));
        assertNotNull(TimetableRules.slotProblem("Module 1", DAY, NINE, ELEVEN, "COMPLETED", START, null));
        assertNotNull(TimetableRules.slotProblem("M", DAY, NINE, ELEVEN, "PLANNED", START, null));
        assertNotNull(TimetableRules.slotProblem("Module 1", null, NINE, ELEVEN, "PLANNED", START, null));
        assertTrue(TimetableRules.slotProblem("Module 1", DAY, ELEVEN, NINE, "RUNNING", START, null).contains("after the start"));
        assertTrue(TimetableRules.slotProblem("Module 1", DAY, NINE, NINE, "RUNNING", START, null).contains("after the start"));
        assertTrue(TimetableRules.slotProblem("Module 1", START.minusDays(1), NINE, ELEVEN, "PLANNED", START, null).contains("before the cohort starts"));
        assertTrue(TimetableRules.slotProblem("Module 1", DAY, NINE, ELEVEN, "PLANNED", START, DAY.minusDays(1)).contains("after the cohort ends"));
        assertNull(TimetableRules.slotProblem("Module 1", DAY, NINE, ELEVEN, "PLANNED", START, DAY));
    }

    @Test
    void venueMustBeActiveAtTheCohortSiteAndBigEnough() {
        assertNull(TimetableRules.venueProblem(true, S1, S1, 20, 20));
        assertNull(TimetableRules.venueProblem(true, S1, S1, null, 500));
        assertNotNull(TimetableRules.venueProblem(false, S1, S1, 20, 5));
        assertTrue(TimetableRules.venueProblem(true, S2, S1, 20, 5).contains("different site"));
        assertTrue(TimetableRules.venueProblem(true, S1, S1, 20, 21).contains("holds 20"));
    }

    @Test
    void facilitatorMustBeOnTheCohortAndInForceOnTheSlotDate() {
        assertNull(TimetableRules.facilitatorProblem(true, PractitionerRules.Standing.VERIFIED_IN_FORCE, DAY));
        assertTrue(TimetableRules.facilitatorProblem(false, PractitionerRules.Standing.VERIFIED_IN_FORCE, DAY).contains("Assign them"));
        for (PractitionerRules.Standing s : PractitionerRules.Standing.values()) {
            if (s != PractitionerRules.Standing.VERIFIED_IN_FORCE) {
                assertTrue(TimetableRules.facilitatorProblem(true, s, DAY).contains("2027-02-10"));
            }
        }
    }

    private static final java.time.LocalDate D = java.time.LocalDate.of(2027, 3, 1);

    @Test
    void instantsDependOnTheZone() {
        java.time.Instant jhb = TimetableRules.instant(D, NINE, "Africa/Johannesburg");
        java.time.Instant utc = TimetableRules.instant(D, NINE, "UTC");
        assertEquals(java.time.Duration.ofHours(2), java.time.Duration.between(jhb, utc));
    }

    @Test
    void overlapOnRealMomentsAcrossZones() {
        java.time.Instant aStart = TimetableRules.instant(D, NINE, "Africa/Johannesburg");
        java.time.Instant aEnd = TimetableRules.instant(D, ELEVEN, "Africa/Johannesburg");
        // 08:00-10:00 UTC is 10:00-12:00 in Johannesburg, so it starts exactly when 09:00-11:00 is half done
        assertTrue(TimetableRules.overlaps(aStart, aEnd, TimetableRules.instant(D, LocalTime.of(8, 0), "UTC"), TimetableRules.instant(D, LocalTime.of(10, 0), "UTC")));
        // 09:00-11:00 UTC is 11:00-13:00 in Johannesburg: back to back, no clash
        assertFalse(TimetableRules.overlaps(aStart, aEnd, TimetableRules.instant(D, NINE, "UTC"), TimetableRules.instant(D, ELEVEN, "UTC")));
    }

    @Test
    void zonesMustExist() {
        assertNull(TimetableRules.zoneProblem("Africa/Johannesburg"));
        assertNotNull(TimetableRules.zoneProblem(null));
        assertNotNull(TimetableRules.zoneProblem(" "));
        assertNotNull(TimetableRules.zoneProblem("Mars/Olympus"));
    }

    private static TimetableRules.Clash clash(String message) {
        return new TimetableRules.Clash(TimetableRules.VENUE, java.util.UUID.randomUUID(), "Room 1", java.util.UUID.randomUUID(), java.util.UUID.randomUUID(), message);
    }

    @Test
    void noClashNeedsNoOverride() {
        assertNull(TimetableRules.overrideProblem(java.util.List.of(), null, false));
        assertNull(TimetableRules.overrideProblem(null, null, false));
    }

    @Test
    void aClashNeedsAReasonAndAnApprover() {
        java.util.List<TimetableRules.Clash> clashes = java.util.List.of(clash("Room 1 is booked"), clash("Thandi is in another cohort"));
        assertTrue(TimetableRules.overrideProblem(clashes, null, true).contains("Room 1 is booked; Thandi is in another cohort"));
        assertNotNull(TimetableRules.overrideProblem(clashes, "too short", true));
        assertTrue(TimetableRules.overrideProblem(clashes, "Both groups fit in the hall", false).contains("Only an administrator"));
        assertNull(TimetableRules.overrideProblem(clashes, "Both groups fit in the hall", true));
    }
}
