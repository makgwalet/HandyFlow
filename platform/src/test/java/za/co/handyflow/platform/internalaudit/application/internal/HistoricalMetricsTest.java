package za.co.handyflow.platform.internalaudit.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.internalaudit.application.internal.DashboardTrends.Metrics;
import za.co.handyflow.platform.internalaudit.application.internal.HistoricalMetrics.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HistoricalMetricsTest {
    static final ZoneId Z = ZoneId.of("Africa/Johannesburg");
    static final LocalDate DAY = LocalDate.of(2026, 8, 1);

    static Instant at(int offsetDays) { return DAY.plusDays(offsetDays).atTime(10, 0).atZone(Z).toInstant(); }
    static Metrics asAt(List<EngagementIn> e, List<FindingIn> f, List<ActionIn> a, List<AreaIn> r) { return HistoricalMetrics.asAt(DAY, e, f, a, r); }

    @Test void engagementsCountedFromTheDayTheyWereCreated() {
        var e = List.of(new EngagementIn(at(-5)), new EngagementIn(at(0)), new EngagementIn(at(1)));
        assertEquals(2, asAt(e, List.of(), List.of(), List.of()).engagements());
    }

    @Test void aFindingIsOpenFromRaisedUntilTheDayItWasResolvedOrClosed() {
        var f = List.of(
                new FindingIn(at(-10), at(-3), null, "RESOLVED"),        // ended before the day: not open
                new FindingIn(at(-10), at(2), null, "RESOLVED"),         // ended after the day: open then
                new FindingIn(at(-10), at(5), at(-1), "CLOSED"),         // earliest end counts: not open
                new FindingIn(at(-10), null, null, "OPEN"),              // open now: open then
                new FindingIn(at(3), null, null, "OPEN"),                // not raised yet
                new FindingIn(at(-10), at(-8), null, "IN_PROGRESS"));    // reopened since: counted open throughout
        assertEquals(3, asAt(List.of(), f, List.of(), List.of()).openFindings());
    }

    @Test void anActionIsOverdueFromTheDayAfterItsDueDateUntilItIsValidated() {
        var a = List.of(
                new ActionIn(at(-20), DAY.minusDays(1), null, "OPEN"),                // overdue
                new ActionIn(at(-20), DAY, null, "OPEN"),                             // due that day: not yet overdue
                new ActionIn(at(-20), DAY.minusDays(5), at(-2), "VALIDATED"),         // validated before: no
                new ActionIn(at(-20), DAY.minusDays(5), at(4), "VALIDATED"),          // validated later: was overdue
                new ActionIn(at(-20), DAY.minusDays(5), null, "CANCELLED"),           // cancelled: left out
                new ActionIn(at(2), DAY.minusDays(5), null, "OPEN"));                 // not created yet
        assertEquals(2, asAt(List.of(), List.of(), a, List.of()).overdueActions());
    }

    @Test void anAreaIsHighRiskByItsLatestRatingOnOrBeforeTheDay() {
        var areas = List.of(
                new AreaIn(at(-30), List.of(new RatingIn(at(-20), "LOW"), new RatingIn(at(-5), "HIGH"), new RatingIn(at(10), "LOW"))),   // high on the day
                new AreaIn(at(-30), List.of(new RatingIn(at(-20), "CRITICAL"), new RatingIn(at(-5), "MEDIUM"))),                          // eased before
                new AreaIn(at(-30), List.of(new RatingIn(at(6), "CRITICAL"))),                                                          // assessed later
                new AreaIn(at(-30), List.of()),                                                                                         // never assessed
                new AreaIn(at(5), List.of(new RatingIn(at(-1), "HIGH"))));                                                              // not in the universe yet
        assertEquals(1, asAt(List.of(), List.of(), List.of(), areas).highRiskAreas());
    }

    @Test void theEndOfTheDayIsInSouthAfricanTime() {
        // 23:30 on the day in Johannesburg is 21:30 UTC, still that day; 00:30 next day is not
        var late = DAY.atTime(23, 30).atZone(Z).toInstant();
        var next = DAY.plusDays(1).atTime(0, 30).atZone(Z).toInstant();
        assertEquals(1, asAt(List.of(new EngagementIn(late), new EngagementIn(next)), List.of(), List.of(), List.of()).engagements());
    }

    @Test void earliestIsTheOldestRecordOfAnyKind() {
        assertNull(HistoricalMetrics.earliest(List.of(), List.of(), List.of(), List.of()));
        var d = HistoricalMetrics.earliest(List.of(new EngagementIn(at(-2))), List.of(new FindingIn(at(-9), null, null, "OPEN")), List.of(), List.of(new AreaIn(at(-4), List.of())));
        assertEquals(DAY.minusDays(9), d);
    }
}
