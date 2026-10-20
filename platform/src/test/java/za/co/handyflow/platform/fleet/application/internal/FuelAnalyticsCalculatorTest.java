package za.co.handyflow.platform.fleet.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.fleet.application.internal.FuelAnalyticsCalculator.*;
import za.co.handyflow.platform.fleet.domain.model.FuelFillup;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class FuelAnalyticsCalculatorTest {
    TenantId tenant = TenantId.generate();
    UUID vehicle = UUID.randomUUID();
    LocalDate day0 = LocalDate.of(2026, 3, 1);

    FuelFillup fill(int dayOffset, String litres, Integer odo, boolean full) { return fill(dayOffset, litres, odo, full, null, null); }
    FuelFillup fill(int dayOffset, String litres, Integer odo, boolean full, String receipt, String total) {
        return FuelFillup.create(tenant, vehicle, day0.plusDays(dayOffset), new BigDecimal(litres), null,
                total == null ? null : new BigDecimal(total), odo, "Engen", receipt, full);
    }

    // ── Full-tank to full-tank ──
    @Test void consumptionIsMeasuredBetweenTwoFullTanks() {
        List<Segment> s = FuelAnalyticsCalculator.segments(List.of(fill(0, "50", 10_000, true), fill(5, "40", 10_500, true)), null, 20);
        assertEquals(1, s.size());
        assertEquals(500, s.get(0).km());
        assertEquals(8.0, s.get(0).litresPer100Km(), 0.0001);
        assertEquals(12.5, s.get(0).kmPerLitre(), 0.0001);
    }

    @Test void partialFillsBetweenAreAddedToTheLitres() {
        List<Segment> s = FuelAnalyticsCalculator.segments(List.of(
                fill(0, "50", 10_000, true), fill(2, "20", 10_200, false), fill(5, "20", 10_500, true)), null, 20);
        assertEquals(1, s.size());
        assertEquals(40.0, s.get(0).litres(), 0.0001);
        assertEquals(1, s.get(0).partialFills());
    }

    @Test void partialFillsBeforeTheFirstFullTankCannotAnchorAMeasurement() {
        List<Segment> s = FuelAnalyticsCalculator.segments(List.of(
                fill(0, "20", 9_800, false), fill(1, "50", 10_000, true), fill(5, "40", 10_500, true)), null, 20);
        assertEquals(1, s.size());
        assertEquals(40.0, s.get(0).litres(), 0.0001);
    }

    @Test void aFullTankWithoutAnOdometerBreaksTheChain() {
        List<Segment> s = FuelAnalyticsCalculator.segments(List.of(
                fill(0, "50", 10_000, true), fill(3, "30", null, true), fill(6, "40", 10_700, true), fill(9, "35", 11_200, true)), null, 20);
        assertEquals(1, s.size()); // only the stretch after the unanchored fill-up is measured
        assertEquals(500, s.get(0).km());
    }

    @Test void anOdometerThatGoesBackwardsIsIgnoredAndAFullTankRestartsTheChain() {
        List<Segment> s = FuelAnalyticsCalculator.segments(List.of(
                fill(0, "50", 10_000, true), fill(3, "30", 9_000, false), fill(6, "40", 9_500, true), fill(9, "30", 10_000, true)), null, 20);
        assertEquals(1, s.size());
        assertEquals(9_500, s.get(0).start().getOdometerAtFillup().intValue());
    }

    @Test void shortStretchesAreNotAssessed() {
        List<Segment> s = FuelAnalyticsCalculator.segments(List.of(fill(0, "50", 10_000, true), fill(1, "10", 10_049, true)), 9.0, 20);
        assertEquals(SegmentStatus.NOT_ASSESSED, s.get(0).status());
        assertFalse(s.get(0).isAssessable());
    }

    @Test void impossibleFiguresAreFlaggedAsSuspectData() {
        List<Segment> s = FuelAnalyticsCalculator.segments(List.of(fill(0, "50", 10_000, true), fill(2, "400", 10_100, true)), null, 20);
        assertEquals(SegmentStatus.DATA_SUSPECT, s.get(0).status());
        assertTrue(s.get(0).isAbnormal());
        assertFalse(s.get(0).isAssessable());
    }

    // ── Baselines ──
    @Test void aManualExpectedFigureIsUsedAndTheToleranceDecidesHighAndLow() {
        List<FuelFillup> f = List.of(fill(0, "50", 10_000, true),
                fill(5, "50", 10_500, true),   // 10.0 L/100: +11% on 9.0 -> normal
                fill(10, "60", 11_000, true),  // 12.0 -> +33% -> high
                fill(15, "30", 11_500, true)); // 6.0 -> -33% -> below -(20+10)% -> low
        List<Segment> s = FuelAnalyticsCalculator.segments(f, 9.0, 20);
        assertEquals(SegmentStatus.NORMAL, s.get(0).status());
        assertEquals(SegmentStatus.HIGH_CONSUMPTION, s.get(1).status());
        assertEquals(SegmentStatus.LOW_CONSUMPTION, s.get(2).status());
        assertEquals(BaselineSource.MANUAL, s.get(1).baselineSource());
    }

    @Test void lowConsumptionNeedsTenMorePointsThanHighBeforeItIsFlagged() {
        // 25% below baseline: more than 20% but less than 30%, so only normal.
        List<Segment> s = FuelAnalyticsCalculator.segments(List.of(fill(0, "50", 10_000, true), fill(5, "45", 10_600, true)), 10.0, 20);
        assertEquals(7.5, s.get(0).litresPer100Km(), 0.0001);
        assertEquals(SegmentStatus.NORMAL, s.get(0).status());
    }

    @Test void withoutAFigureTheBaselineIsTheMedianOfThePreviousSegmentsOnceThereAreEnough() {
        List<FuelFillup> f = new ArrayList<>();
        int odo = 10_000;
        f.add(fill(0, "50", odo, true));
        double[] l100 = {9, 9, 9, 9, 13};
        int day = 5;
        for (double v : l100) { odo += 500; f.add(fill(day, String.valueOf(v * 5), odo, true)); day += 5; }
        List<Segment> s = FuelAnalyticsCalculator.segments(f, null, 20);
        assertEquals(SegmentStatus.NO_BASELINE, s.get(0).status());
        assertEquals(SegmentStatus.NO_BASELINE, s.get(2).status()); // only two earlier segments
        assertEquals(SegmentStatus.NORMAL, s.get(3).status());
        assertEquals(BaselineSource.ROLLING_MEDIAN, s.get(3).baselineSource());
        assertEquals(9.0, s.get(4).baseline(), 0.0001);
        assertEquals(SegmentStatus.HIGH_CONSUMPTION, s.get(4).status());
    }

    @Test void oneBadSegmentDoesNotDragTheMedianBaseline() {
        assertEquals(9.0, FuelAnalyticsCalculator.median(List.of(9.0, 9.0, 9.0, 40.0, 9.0)), 0.0001);
        assertEquals(10.0, FuelAnalyticsCalculator.median(List.of(8.0, 12.0)), 0.0001);
        assertEquals(7.0, FuelAnalyticsCalculator.median(List.of(7.0)), 0.0001);
    }

    // ── Cost ──
    @Test void costIsTheRecordedTotalOrPriceTimesLitresOrUnknown() {
        assertEquals(0, new BigDecimal("500.00").compareTo(FuelAnalyticsCalculator.costOf(fill(0, "40", 1, true, null, "500.00"))));
        FuelFillup priced = FuelFillup.create(tenant, vehicle, day0, new BigDecimal("40"), new BigDecimal("23.456"), null, 1, null, null, true);
        assertEquals(0, new BigDecimal("938.24").compareTo(FuelAnalyticsCalculator.costOf(priced)));
        assertNull(FuelAnalyticsCalculator.costOf(fill(0, "40", 1, true)));
    }

    @Test void aSegmentCostIsUnknownIfAnyFillUpInItHasNoCost() {
        List<Segment> known = FuelAnalyticsCalculator.segments(List.of(fill(0, "50", 10_000, true, null, "800"), fill(5, "40", 10_500, true, null, "700")), null, 20);
        assertEquals(0, new BigDecimal("700").compareTo(known.get(0).cost()));
        List<Segment> unknown = FuelAnalyticsCalculator.segments(List.of(fill(0, "50", 10_000, true, null, "800"), fill(5, "40", 10_500, true)), null, 20);
        assertNull(unknown.get(0).cost());
    }

    // ── Indicators ──
    LocalDate from = LocalDate.of(2026, 1, 1), to = LocalDate.of(2026, 12, 31);

    @Test void fillingMoreThanTheTankHoldsIsFlagged() {
        List<Indicator> i = FuelAnalyticsCalculator.indicators(List.of(fill(0, "90", 100, true)), new BigDecimal("80"), Map.of(), from, to);
        assertEquals(1, i.size());
        assertEquals(IndicatorType.OVERFILL, i.get(0).type());
        assertTrue(FuelAnalyticsCalculator.indicators(List.of(fill(0, "83", 100, true)), new BigDecimal("80"), Map.of(), from, to).isEmpty()); // within 5%
    }

    @Test void severalSmallFillsInOneDayThatExceedTheTankAreFlaggedOnceNotAsOverfill() {
        List<Indicator> i = FuelAnalyticsCalculator.indicators(
                List.of(fill(0, "50", 100, true), fill(0, "50", 120, true)), new BigDecimal("80"), Map.of(), from, to);
        assertEquals(1, i.size());
        assertEquals(IndicatorType.SAME_DAY_MULTIPLE, i.get(0).type());
    }

    @Test void theSameReceiptOnTwoFillUpsIsAnAlert() {
        List<FuelFillup> f = List.of(fill(0, "30", 100, true, " ab-1 ", null), fill(3, "30", 400, true, "AB-1", null));
        List<Indicator> i = FuelAnalyticsCalculator.indicators(f, null, FuelAnalyticsCalculator.receiptCounts(f), from, to);
        assertEquals(2, i.size());
        assertEquals(Severity.ALERT, i.get(0).severity());
        assertEquals(IndicatorType.DUPLICATE_RECEIPT, i.get(0).type());
        assertNull(FuelAnalyticsCalculator.normaliseReceipt("   "));
    }

    @Test void fuelWithNoDistanceAndALowerOdometerAreBothFlagged() {
        List<FuelFillup> f = List.of(fill(0, "40", 1_000, true), fill(2, "40", 1_000, true), fill(4, "20", 900, true));
        List<Indicator> i = FuelAnalyticsCalculator.indicators(f, null, Map.of(), from, to);
        assertEquals(IndicatorType.NO_DISTANCE, i.get(0).type());
        assertEquals(IndicatorType.ODOMETER_REGRESSION, i.get(1).type());
    }

    @Test void aSmallTopUpWithNoDistanceIsNotFlaggedWhenNoTankSizeIsKnown() {
        List<Indicator> i = FuelAnalyticsCalculator.indicators(List.of(fill(0, "40", 1_000, true), fill(2, "10", 1_000, false)), null, Map.of(), from, to);
        assertTrue(i.isEmpty());
    }

    @Test void oneBadOdometerDoesNotPoisonTheComparisonForLaterFillUps() {
        List<FuelFillup> f = List.of(fill(0, "40", 1_000, true), fill(2, "40", 100, true), fill(4, "40", 1_400, true));
        List<Indicator> i = FuelAnalyticsCalculator.indicators(f, null, Map.of(), from, to);
        assertEquals(1, i.size()); // only the 100; 1,400 is compared with 1,000 and is fine
        assertEquals(IndicatorType.ODOMETER_REGRESSION, i.get(0).type());
    }

    @Test void onlyFillUpsInsideTheWindowAreReportedButEarlierOnesStillProvideThePreviousOdometer() {
        List<FuelFillup> f = List.of(fill(-60, "40", 1_000, true), fill(0, "40", 900, true));
        List<Indicator> i = FuelAnalyticsCalculator.indicators(f, null, Map.of(), day0, day0.plusDays(30));
        assertEquals(1, i.size());
        assertEquals(IndicatorType.ODOMETER_REGRESSION, i.get(0).type());
    }

    @Test void fillUpsAreGroupedPerVehicleInTheOrderGiven() {
        UUID other = UUID.randomUUID();
        FuelFillup a = fill(0, "10", 1, true), b = FuelFillup.create(tenant, other, day0, new BigDecimal("10"), null, null, 1, null, null, true), c = fill(1, "10", 2, true);
        Map<UUID, List<FuelFillup>> g = FuelAnalyticsCalculator.byVehicle(List.of(a, b, c));
        assertEquals(List.of(a, c), g.get(vehicle));
        assertEquals(List.of(b), g.get(other));
    }
}
