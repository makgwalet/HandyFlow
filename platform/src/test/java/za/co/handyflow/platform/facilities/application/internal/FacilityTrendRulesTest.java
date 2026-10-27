package za.co.handyflow.platform.facilities.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.facilities.application.internal.FacilityTrendRules.Done;
import za.co.handyflow.platform.facilities.dto.FacilityTrendResponse.Point;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FacilityTrendRulesTest {

    // 2026-10-07 is a Wednesday; its ISO week starts Monday 2026-10-05.
    private static final LocalDate WED = LocalDate.of(2026, 10, 7);

    private static Instant at(LocalDate d, int hour) { return d.atTime(hour, 0).atZone(FacilityTime.ZONE).toInstant(); }

    private static Done done(LocalDate created, LocalDate completed, int rework, Integer downtime, String cost) {
        return new Done(at(created, 8), at(completed, 8), rework, downtime, cost == null ? null : new BigDecimal(cost));
    }

    @Test
    void weeksStartOnMondayAndEmptyWeeksAreIncluded() {
        List<Point> p = FacilityTrendRules.trend(WED, WED.plusDays(20), "WEEK", List.of(), List.of());
        assertEquals(List.of(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 26)),
                p.stream().map(Point::start).toList());
        assertEquals(LocalDate.of(2026, 10, 11), p.get(0).end());
        assertTrue(p.stream().allMatch(x -> x.raised() == 0 && x.completed() == 0 && x.avgCompletionHours() == null
                && x.cost().signum() == 0));
    }

    @Test
    void raisedAndCompletedAreCountedInTheirOwnBuckets() {
        LocalDate nextWeek = WED.plusDays(7);
        List<Point> p = FacilityTrendRules.trend(WED, nextWeek, "WEEK",
                List.of(WED, WED, nextWeek),
                List.of(done(WED, WED, 0, null, null), done(WED, nextWeek, 1, 30, "100.50"), done(WED, nextWeek, 0, 15, "49.50")));
        assertEquals(2, p.size());
        assertEquals(2, p.get(0).raised());
        assertEquals(1, p.get(0).completed());
        assertEquals(1, p.get(1).raised());
        assertEquals(2, p.get(1).completed());
        assertEquals(1, p.get(1).reworked());
        assertEquals(45, p.get(1).downtimeMinutes());
        assertEquals(new BigDecimal("150.00"), p.get(1).cost());
    }

    @Test
    void averageCompletionTimeIsCreatedToCompleted() {
        // 2 days = 48h and 4 days = 96h -> average 72h
        List<Point> p = FacilityTrendRules.trend(WED, WED.plusDays(6), "WEEK", List.of(),
                List.of(done(WED.minusDays(2), WED, 0, null, null), done(WED.minusDays(4), WED, 0, null, null)));
        assertEquals(new BigDecimal("72.00"), p.get(0).avgCompletionHours());
    }

    @Test
    void monthsStartOnTheFirst() {
        List<Point> p = FacilityTrendRules.trend(LocalDate.of(2026, 8, 20), LocalDate.of(2026, 10, 3), "month",
                List.of(LocalDate.of(2026, 8, 25), LocalDate.of(2026, 10, 1)), List.of());
        assertEquals(List.of(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 1)),
                p.stream().map(Point::start).toList());
        assertEquals(LocalDate.of(2026, 8, 31), p.get(0).end());
        assertEquals(LocalDate.of(2026, 10, 31), p.get(2).end());
        assertEquals(1, p.get(0).raised());
        assertEquals(0, p.get(1).raised());
        assertEquals(1, p.get(2).raised());
    }

    @Test
    void workOutsideThePeriodIsIgnored() {
        List<Point> p = FacilityTrendRules.trend(WED, WED.plusDays(3), "WEEK", List.of(WED.minusDays(30), WED.plusDays(60)),
                List.of(done(WED, WED.plusDays(60), 0, null, null)));
        assertEquals(0, p.stream().mapToLong(Point::raised).sum());
        assertEquals(0, p.stream().mapToLong(Point::completed).sum());
    }

    @Test
    void bucketDefaultsToWeekAndRejectsOthers() {
        assertEquals("WEEK", FacilityTrendRules.normaliseBucket(null));
        assertEquals("WEEK", FacilityTrendRules.normaliseBucket(" "));
        assertEquals("MONTH", FacilityTrendRules.normaliseBucket("month"));
        assertThrows(IllegalArgumentException.class, () -> FacilityTrendRules.normaliseBucket("DAY"));
    }
}
