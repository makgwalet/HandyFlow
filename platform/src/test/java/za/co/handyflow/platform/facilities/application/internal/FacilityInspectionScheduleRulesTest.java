package za.co.handyflow.platform.facilities.application.internal;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class FacilityInspectionScheduleRulesTest {

    private static final LocalDate DUE = LocalDate.of(2026, 11, 1);

    @Test
    void doneOnTimeKeepsTheCadence() {
        assertEquals(LocalDate.of(2026, 12, 1), FacilityInspectionScheduleRules.nextDue(DUE, 30, DUE));
    }

    @Test
    void doneEarlyDoesNotShiftTheCalendar() {
        assertEquals(LocalDate.of(2026, 12, 1), FacilityInspectionScheduleRules.nextDue(DUE, 30, DUE.minusDays(5)));
    }

    @Test
    void doneALittleLateStaysAnchoredWhileTheNextDateIsStillAhead() {
        // due 1 Nov, done 10 Nov: anchored next is 1 Dec, still in the future.
        assertEquals(LocalDate.of(2026, 12, 1), FacilityInspectionScheduleRules.nextDue(DUE, 30, DUE.plusDays(9)));
    }

    @Test
    void aNeglectedScheduleRestartsFromTheDayItWasDone() {
        // due 1 Nov, done 5 Jan (anchored 1 Dec is already past): next is a full interval after completion.
        LocalDate done = LocalDate.of(2027, 1, 5);
        assertEquals(done.plusDays(30), FacilityInspectionScheduleRules.nextDue(DUE, 30, done));
    }

    @Test
    void completionOnTheAnchoredDayCountsAsNeglected() {
        // anchored == completion day is not "after": restart so it is not due again immediately.
        LocalDate done = DUE.plusDays(30);
        assertEquals(done.plusDays(30), FacilityInspectionScheduleRules.nextDue(DUE, 30, done));
    }

    @Test
    void remindsOnLeadDaysAndTheDay() {
        for (long d : new long[]{7, 3, 1, 0}) assertTrue(FacilityInspectionScheduleRules.shouldRemind(d), "day " + d);
        for (long d : new long[]{30, 14, 8, 6, 5, 4, 2}) assertFalse(FacilityInspectionScheduleRules.shouldRemind(d), "day " + d);
    }

    @Test
    void overdueUsesTheSameMilestonesAsWorkOrders() {
        for (long late : new long[]{1, 3, 7, 14, 30, 60}) assertTrue(FacilityInspectionScheduleRules.shouldRemind(-late), "late " + late);
        for (long late : new long[]{2, 5, 20, 31, 45}) assertFalse(FacilityInspectionScheduleRules.shouldRemind(-late), "late " + late);
    }
}
