package za.co.handyflow.platform.fleet.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.fleet.application.internal.DriverScoreCalculator.ComponentResult;
import za.co.handyflow.platform.fleet.application.internal.DriverScoreCalculator.Input;
import za.co.handyflow.platform.fleet.application.internal.DriverScoreCalculator.Result;
import za.co.handyflow.platform.fleet.domain.model.*;
import za.co.handyflow.platform.fleet.dto.ComplianceItemResponse;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class DriverScoreCalculatorTest {
    TenantId tenant = TenantId.generate();
    LocalDate today = LocalDate.of(2026, 10, 20);
    UUID vehicle = UUID.randomUUID();
    DriverScoreSettings settings = DriverScoreSettings.defaults(tenant);
    Instant t0 = Instant.parse("2026-10-01T06:00:00Z");

    Driver driver(LocalDate licence, boolean prdp, LocalDate prdpExpiry) {
        return Driver.create(tenant, "Thabo", "M", null, null, null, "L1", "EC", licence, prdp, prdp ? "P1" : null, null, prdpExpiry, null);
    }

    Trip trip(int i, boolean closedProperly, BigDecimal fuel, int km) {
        Trip t = Trip.create(tenant, vehicle, null, UUID.randomUUID(), "Thabo", "Delivery", "BUSINESS", "Depot", 1000, t0.plusSeconds(86_400L * i));
        t.complete("Site", 1000 + km, closedProperly ? t0.plusSeconds(86_400L * i + 3600) : null, fuel, null);
        return t;
    }

    Result score(Driver d, List<Trip> trips, List<Accident> accidents) {
        return DriverScoreCalculator.calculate(new Input(d, trips, accidents, List.of(), Map.of(), today, settings, List.of(), Set.of()));
    }

    ComponentResult part(Result r, ScoreComponent c) { return r.components().stream().filter(x -> x.component() == c).findFirst().orElseThrow(); }

    // ── Licence and PrDP ──
    @Test void licenceStatusRunsFromValidThroughExpiringSoonToExpired() {
        assertEquals(ComplianceStatus.VALID, DriverScoreCalculator.complianceItems(driver(today.plusDays(30), false, null), today).get(0).status());
        assertEquals(ComplianceStatus.EXPIRING_SOON, DriverScoreCalculator.complianceItems(driver(today.plusDays(29), false, null), today).get(0).status());
        assertEquals(ComplianceStatus.EXPIRING_SOON, DriverScoreCalculator.complianceItems(driver(today, false, null), today).get(0).status());
        assertEquals(ComplianceStatus.EXPIRED, DriverScoreCalculator.complianceItems(driver(today.minusDays(1), false, null), today).get(0).status());
        assertEquals(ComplianceStatus.MISSING, DriverScoreCalculator.complianceItems(driver(null, false, null), today).get(0).status());
    }

    @Test void aPrdpIsCheckedOnlyForDriversWhoNeedOne() {
        assertEquals(1, DriverScoreCalculator.complianceItems(driver(today.plusDays(200), false, null), today).size());
        List<ComplianceItemResponse> two = DriverScoreCalculator.complianceItems(driver(today.plusDays(200), true, today.minusDays(5)), today);
        assertEquals(2, two.size());
        assertEquals(ComplianceStatus.EXPIRED, two.get(1).status());
    }

    @Test void complianceTakesTheWorstDocument() {
        Result r = score(driver(today.plusDays(200), true, today.minusDays(5)), List.of(), List.of());
        assertEquals(0, part(r, ScoreComponent.COMPLIANCE).score().intValue());
        Result ok = score(driver(today.plusDays(200), true, today.plusDays(200)), List.of(), List.of());
        assertEquals(100, part(ok, ScoreComponent.COMPLIANCE).score().intValue());
        Result soon = score(driver(today.plusDays(10), false, null), List.of(), List.of());
        assertEquals(70, part(soon, ScoreComponent.COMPLIANCE).score().intValue());
        assertEquals(50, part(score(driver(null, false, null), List.of(), List.of()), ScoreComponent.COMPLIANCE).score().intValue());
    }

    // ── Not enough data ──
    @Test void aComponentWithTooLittleDataHasNoScoreAndNoWeight() {
        Result r = score(driver(today.plusDays(200), false, null), List.of(), List.of());
        assertNull(part(r, ScoreComponent.SAFETY).score());
        assertNull(part(r, ScoreComponent.TRIPS).score());
        assertEquals(0.0, part(r, ScoreComponent.SAFETY).effectiveWeight(), 0.0001);
        assertEquals(100.0, part(r, ScoreComponent.COMPLIANCE).effectiveWeight(), 0.0001); // only one scored: it carries everything
    }

    @Test void coverageIsTheShareOfWeightThatHadData() {
        Result r = score(driver(today.plusDays(200), false, null), List.of(), List.of());
        assertEquals(10.0, r.coverage(), 0.0001); // compliance is 10 of 100
        assertFalse(r.reliable());
        assertEquals(100, r.overall().intValue());
    }

    @Test void missingDataIsSpreadOverWhatRemainsSoItNeverDragsTheScoreDown() {
        List<Trip> trips = List.of(trip(1, true, null, 100), trip(2, true, null, 100), trip(3, true, null, 100));
        Result r = score(driver(today.plusDays(200), false, null), trips, List.of());
        double sum = r.components().stream().mapToDouble(ComponentResult::effectiveWeight).sum();
        assertEquals(100.0, sum, 0.2);
        // Safety and fuel have no data and are left out; inspections are scored 0 (no inspection before any of the three trips).
        assertNull(part(r, ScoreComponent.SAFETY).score());
        assertEquals(0, part(r, ScoreComponent.INSPECTION).score().intValue());
        assertEquals(69, r.overall().intValue()); // (20*0 + 25*100 + 10*100 + 10*100) / 65
    }

    // ── Accidents ──
    @Test void noTripsAndNoAccidentsMeansNothingToJudge() {
        assertNull(part(score(driver(today.plusDays(200), false, null), List.of(), List.of()), ScoreComponent.ACCIDENTS).score());
    }

    @Test void anAccidentFreePeriodScoresFullMarks() {
        List<Trip> trips = List.of(trip(1, true, null, 50));
        assertEquals(100, part(score(driver(today.plusDays(200), false, null), trips, List.of()), ScoreComponent.ACCIDENTS).score().intValue());
    }

    @Test void eachAccidentCostsTwentyPointsAndInjuriesAndDeathsCostMore() {
        Accident minor = accident(0, 0), injury = accident(1, 0), fatal = accident(0, 1);
        Driver d = driver(today.plusDays(200), false, null);
        assertEquals(80, part(score(d, List.of(), List.of(minor)), ScoreComponent.ACCIDENTS).score().intValue());
        assertEquals(65, part(score(d, List.of(), List.of(injury)), ScoreComponent.ACCIDENTS).score().intValue());
        assertEquals(30, part(score(d, List.of(), List.of(fatal)), ScoreComponent.ACCIDENTS).score().intValue());
        assertEquals(0, part(score(d, List.of(), List.of(fatal, fatal, fatal)), ScoreComponent.ACCIDENTS).score().intValue()); // never below zero
    }

    Accident accident(int injuries, int fatalities) {
        AccidentDetails det = new AccidentDetails(null, "Thabo", null, t0, "N1", null, null, null, null, null, null, null, null, null,
                "Rear-ended", null, injuries, fatalities, null, null, true);
        return Accident.report(tenant, "ACC-1", vehicle, det, null);
    }

    // ── Trips ──
    @Test void tripComplianceCountsTripsClosedWithAnEndOdometerAndTime() {
        List<Trip> trips = List.of(trip(1, true, null, 50), trip(2, true, null, 50), trip(3, false, null, 50), trip(4, true, null, 50));
        ComponentResult c = part(score(driver(today.plusDays(200), false, null), trips, List.of()), ScoreComponent.TRIPS);
        assertEquals(75, c.score().intValue());
    }

    @Test void tooFewFinishedTripsGiveNoTripScore() {
        List<Trip> trips = List.of(trip(1, true, null, 50), trip(2, true, null, 50));
        assertNull(part(score(driver(today.plusDays(200), false, null), trips, List.of()), ScoreComponent.TRIPS).score());
    }

    // ── Fuel ──
    @Test void fuelIsScoredAgainstEachVehiclesOwnNormAndOnlyOverTheNormCosts() {
        List<Trip> trips = List.of(trip(1, true, new BigDecimal("10"), 100), trip(2, true, new BigDecimal("10"), 100), trip(3, true, new BigDecimal("10"), 100));
        Driver d = driver(today.plusDays(200), false, null);
        // 10 L/100 against a norm of 10 -> 100%
        assertEquals(100, part(DriverScoreCalculator.calculate(new Input(d, trips, List.of(), List.of(), Map.of(vehicle, 10.0), today, settings, List.of(), Set.of())), ScoreComponent.FUEL).score().intValue());
        // 10 against 8 -> 125% of norm -> 100 - 25% * 250 = 37.5 -> 38
        assertEquals(38, part(DriverScoreCalculator.calculate(new Input(d, trips, List.of(), List.of(), Map.of(vehicle, 8.0), today, settings, List.of(), Set.of())), ScoreComponent.FUEL).score().intValue());
        // better than the norm is not rewarded beyond 100
        assertEquals(100, part(DriverScoreCalculator.calculate(new Input(d, trips, List.of(), List.of(), Map.of(vehicle, 15.0), today, settings, List.of(), Set.of())), ScoreComponent.FUEL).score().intValue());
        // no norm for the vehicle: not scored
        assertNull(part(DriverScoreCalculator.calculate(new Input(d, trips, List.of(), List.of(), Map.of(), today, settings, List.of(), Set.of())), ScoreComponent.FUEL).score());
    }

    // ── Safety ──
    @Test void safetyIsNotScoredBelowTheMinimumTrackedKilometres() {
        List<Trip> trips = List.of(trip(1, true, null, 100));
        Input in = new Input(driver(today.plusDays(200), false, null), trips, List.of(), List.of(), Map.of(), today, settings, List.of(), Set.of(vehicle));
        assertNull(part(DriverScoreCalculator.calculate(in), ScoreComponent.SAFETY).score());
    }

    @Test void safetyIsFullMarksWithEnoughTrackedKilometresAndNoEvents() {
        List<Trip> trips = List.of(trip(1, true, null, 250));
        Input in = new Input(driver(today.plusDays(200), false, null), trips, List.of(), List.of(), Map.of(), today, settings, List.of(), Set.of(vehicle));
        assertEquals(100, part(DriverScoreCalculator.calculate(in), ScoreComponent.SAFETY).score().intValue());
    }

    @Test void anUntrackedVehicleDoesNotMakeADriverLookSpotless() {
        List<Trip> trips = List.of(trip(1, true, null, 5_000));
        Input in = new Input(driver(today.plusDays(200), false, null), trips, List.of(), List.of(), Map.of(), today, settings, List.of(), Set.of());
        assertNull(part(DriverScoreCalculator.calculate(in), ScoreComponent.SAFETY).score());
    }

    // ── Bands and ordering ──
    @Test void bandsFollowTheConfiguredThresholds() {
        Result excellent = score(driver(today.plusDays(200), false, null), List.of(), List.of());
        assertEquals("EXCELLENT", excellent.band());
        Result poor = score(driver(today.minusDays(5), false, null), List.of(), List.of());
        assertEquals("POOR", poor.band());
        Result fair = score(driver(null, false, null), List.of(), List.of());
        assertEquals("POOR", fair.band()); // 50 is below the fair line of 60
    }

    @Test void theLeaderboardPutsBestFirstAndUnscoredLast() {
        List<Double> scores = new ArrayList<>(Arrays.asList(70.0, null, 95.5, 82.0));
        scores.sort(DriverScoreCalculator.BEST_FIRST_NULLS_LAST);
        assertEquals(Arrays.asList(95.5, 82.0, 70.0, null), scores);
    }
}
