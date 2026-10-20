package za.co.handyflow.platform.fleet.application.internal;

import za.co.handyflow.platform.fleet.domain.model.Accident;
import za.co.handyflow.platform.fleet.domain.model.ComplianceStatus;
import za.co.handyflow.platform.fleet.domain.model.Driver;
import za.co.handyflow.platform.fleet.domain.model.DriverScoreSettings;
import za.co.handyflow.platform.fleet.domain.model.Inspection;
import za.co.handyflow.platform.fleet.domain.model.ScoreComponent;
import za.co.handyflow.platform.fleet.domain.model.TelematicsEvent;
import za.co.handyflow.platform.fleet.domain.model.TelematicsSeverity;
import za.co.handyflow.platform.fleet.domain.model.Trip;
import za.co.handyflow.platform.fleet.dto.ComplianceItemResponse;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Computes a driver's Safety &amp; Performance Score from data already gathered by the
 * caller. No repositories, no Spring — a pure function of its input, so it can be
 * unit-tested with plain objects.
 * <p>
 * Each component yields 0–100, or NO_DATA when there is not enough to judge. The overall
 * score is the weighted average of the components that HAVE data; weight belonging to
 * NO_DATA components is redistributed in proportion, so a missing data source (e.g.
 * telematics, not yet connected) never silently drags anyone's score down. {@code coverage}
 * reports how much of the configured weight actually had data.
 * <p>
 * The formulas (kept deliberately simple and explainable):
 * <ul>
 *   <li><b>INSPECTION</b> — % of the driver's completed trips preceded, within 24 h before
 *       departure, by a completed inspection of that vehicle linked to the driver.</li>
 *   <li><b>ACCIDENTS</b> — 100 minus 20 per accident, 15 more per accident with injuries,
 *       50 more per accident with fatalities (floor 0). Not fault-adjusted: the accident
 *       register does not record fault, so every accident involving the driver counts.</li>
 *   <li><b>FUEL</b> — the driver's L/100 km on each vehicle versus that vehicle's own norm
 *       (all drivers, over twice the period). At or better than the norm = 100; 10% worse
 *       = 75; 40% worse or more = 0.</li>
 *   <li><b>TRIPS</b> — % of trips closed properly: ended with an end odometer and end time,
 *       and not left running long enough to trigger the long-running-trip alert.</li>
 *   <li><b>COMPLIANCE</b> — the worst of the driver's licence and (if required) PrDP:
 *       valid 100, expiring within 30 days 70, no expiry on file 50, expired 0.</li>
 *   <li><b>SAFETY</b> — telematics events on the driver's trips, as weighted points per 1,000 km
 *       driven in tracked vehicles: speeding 0.5 / 1 / 3 (info / warning / alert), harsh braking 2,
 *       harsh cornering 1.5, harsh acceleration 1, idling 0.5 (geofence crossings and unplugged devices
 *       score nothing — they aren't driving behaviour). Score = 100 − 4 × points per 1,000 km. Needs at
 *       least {@value #SAFETY_MIN_KM} km on tracked vehicles; events outside a trip are not scored.</li>
 * </ul>
 */
final class DriverScoreCalculator {

    /** Minimum trips before a trip-based component is scored. */
    static final int MIN_SAMPLE = 3;
    /** Minimum trips on a vehicle before its fuel-economy norm is trusted. */
    static final int MIN_BASELINE_TRIPS = 5;
    /** Coverage (% of configured weight with data) at or above which a score is called reliable. */
    static final double RELIABLE_COVERAGE = 50.0;

    /** Minimum kilometres on telematics-equipped vehicles before SAFETY is scored. */
    static final int SAFETY_MIN_KM = 200;
    private static final double SAFETY_PENALTY_PER_POINT_PER_1000_KM = 4.0;

    private static final long INSPECTION_WINDOW_BEFORE_HOURS = 24;
    private static final long INSPECTION_WINDOW_AFTER_HOURS = 1;

    private DriverScoreCalculator() {}

    record Input(Driver driver, List<Trip> trips, List<Accident> accidents, List<Inspection> inspections,
                 Map<UUID, Double> fuelBaselines, LocalDate today, DriverScoreSettings settings,
                 List<TelematicsEvent> telematicsEvents, Set<UUID> trackedVehicleIds) {}

    record ComponentResult(ScoreComponent component, int weight, double effectiveWeight,
                           Integer score, String detail) {}

    record Result(List<ComponentResult> components, Double overall, String band,
                  double coverage, boolean reliable) {}

    private record Scored(Integer score, String detail) {
        static Scored of(int score, String detail) { return new Scored(score, detail); }
        static Scored noData(String why) { return new Scored(null, why); }
    }

    // ── Entry points ─────────────────────────────────────────────────────

    static Result calculate(Input in) {
        Map<ScoreComponent, Scored> scored = new EnumMap<>(ScoreComponent.class);
        scored.put(ScoreComponent.SAFETY, safety(in));
        scored.put(ScoreComponent.INSPECTION, inspection(in));
        scored.put(ScoreComponent.ACCIDENTS, accidents(in));
        scored.put(ScoreComponent.FUEL, fuel(in));
        scored.put(ScoreComponent.TRIPS, trips(in));
        scored.put(ScoreComponent.COMPLIANCE, compliance(in));

        int totalWeight = 0;
        int scoredWeight = 0;
        double weightedSum = 0;
        for (ScoreComponent c : ScoreComponent.values()) {
            int w = c.weight(in.settings());
            totalWeight += w;
            Integer s = scored.get(c).score();
            if (s != null && w > 0) {
                scoredWeight += w;
                weightedSum += (double) s * w;
            }
        }

        List<ComponentResult> results = new ArrayList<>();
        for (ScoreComponent c : ScoreComponent.values()) {
            int w = c.weight(in.settings());
            Scored sc = scored.get(c);
            double effective = (sc.score() != null && w > 0 && scoredWeight > 0)
                    ? round1(w * 100.0 / scoredWeight) : 0.0;
            results.add(new ComponentResult(c, w, effective, sc.score(), sc.detail()));
        }

        Double overall = scoredWeight > 0 ? round1(weightedSum / scoredWeight) : null;
        double coverage = totalWeight > 0 ? round1(scoredWeight * 100.0 / totalWeight) : 0.0;
        return new Result(results, overall, band(overall, in.settings()),
                coverage, coverage >= RELIABLE_COVERAGE && overall != null);
    }

    /**
     * Each vehicle's fuel-economy norm in L/100 km, from completed trips with fuel and
     * distance recorded. A vehicle needs at least {@link #MIN_BASELINE_TRIPS} such trips
     * to get a norm; otherwise it is omitted and fuel is not scored against it.
     */
    static Map<UUID, Double> fuelBaselines(List<Trip> baselineTrips) {
        Map<UUID, double[]> acc = new HashMap<>();   // [litres, km, count]
        for (Trip t : baselineTrips) {
            Integer km = t.getDistanceKm();
            if (t.getFuelUsedLitres() == null || km == null || km <= 0
                    || t.getFuelUsedLitres().signum() <= 0) continue;
            double[] a = acc.computeIfAbsent(t.getVehicleId(), k -> new double[3]);
            a[0] += t.getFuelUsedLitres().doubleValue();
            a[1] += km;
            a[2] += 1;
        }
        Map<UUID, Double> out = new HashMap<>();
        acc.forEach((vehicleId, a) -> {
            if (a[2] >= MIN_BASELINE_TRIPS && a[1] > 0) out.put(vehicleId, a[0] / a[1] * 100.0);
        });
        return out;
    }

    /** Licence and (if required) PrDP status — shown on the profile and used as the COMPLIANCE score. */
    static List<ComplianceItemResponse> complianceItems(Driver d, LocalDate today) {
        List<ComplianceItemResponse> items = new ArrayList<>();
        items.add(expiryItem("DRIVING_LICENCE", "Driving licence", d.getLicenseExpiry(), today));
        if (d.isPrdpRequired()) {
            items.add(expiryItem("PRDP", "PrDP", d.getPrdpExpiry(), today));
        }
        return items;
    }

    // ── Components ───────────────────────────────────────────────────────

    private static Scored safety(Input in) {
        // The denominator is km the driver covered on trips in vehicles that actually have a unit —
        // a vehicle with no tracker would otherwise look spotless.
        int km = 0;
        for (Trip t : in.trips()) {
            Integer d = t.getDistanceKm();
            if ("COMPLETED".equals(t.getStatus()) && d != null && d > 0
                    && in.trackedVehicleIds().contains(t.getVehicleId())) {
                km += d;
            }
        }
        if (km < SAFETY_MIN_KM) {
            return Scored.noData("Fewer than " + SAFETY_MIN_KM + " km driven on vehicles with a telematics unit in this period");
        }

        double points = 0;
        int counted = 0;
        Map<String, Integer> byType = new TreeMap<>();
        for (TelematicsEvent e : in.telematicsEvents()) {
            if (e.getTripId() == null) continue;       // events outside a trip aren't attributed to the driver's score
            double w = eventPoints(e);
            if (w <= 0) continue;
            points += w;
            counted++;
            byType.merge(e.getType().name().toLowerCase().replace('_', ' '), 1, Integer::sum);
        }
        double per1000 = points / (km / 1000.0);
        int score = (int) Math.round(clamp(100 - SAFETY_PENALTY_PER_POINT_PER_1000_KM * per1000, 0, 100));
        String breakdown = counted == 0 ? "no events" : byType.entrySet().stream()
                .map(en -> en.getValue() + " " + en.getKey()).reduce((a, b) -> a + ", " + b).orElse("");
        return Scored.of(score, String.format("%,d km on tracked vehicles; %d scoring event(s) (%s) = %.1f points per 1,000 km",
                km, counted, breakdown, per1000));
    }

    private static double eventPoints(TelematicsEvent e) {
        return switch (e.getType()) {
            case SPEEDING -> e.getSeverity() == TelematicsSeverity.ALERT ? 3.0
                    : e.getSeverity() == TelematicsSeverity.WARNING ? 1.0 : 0.5;
            case HARSH_BRAKING -> 2.0;
            case HARSH_CORNERING -> 1.5;
            case HARSH_ACCELERATION -> 1.0;
            case EXCESSIVE_IDLING -> 0.5;
            case GEOFENCE_ENTER, GEOFENCE_EXIT, DEVICE_UNPLUGGED -> 0.0;
        };
    }

    private static Scored inspection(Input in) {
        List<Trip> completed = in.trips().stream().filter(t -> "COMPLETED".equals(t.getStatus())).toList();
        if (completed.size() < MIN_SAMPLE) {
            return Scored.noData("Fewer than " + MIN_SAMPLE + " completed trips linked to this driver");
        }
        int covered = 0;
        for (Trip t : completed) {
            Instant from = t.getStartAt().minus(INSPECTION_WINDOW_BEFORE_HOURS, ChronoUnit.HOURS);
            Instant to = t.getStartAt().plus(INSPECTION_WINDOW_AFTER_HOURS, ChronoUnit.HOURS);
            boolean inspected = in.inspections().stream().anyMatch(i ->
                    i.getVehicleId().equals(t.getVehicleId()) && i.getCompletedAt() != null
                            && !i.getCompletedAt().isBefore(from) && !i.getCompletedAt().isAfter(to));
            if (inspected) covered++;
        }
        return Scored.of(pct(covered, completed.size()), covered + " of " + completed.size()
                + " trips were preceded by a completed inspection within 24 h before departure");
    }

    private static Scored accidents(Input in) {
        if (in.trips().isEmpty() && in.accidents().isEmpty()) {
            return Scored.noData("No trips or accidents recorded for this driver in the period");
        }
        int n = in.accidents().size();
        long injuries = in.accidents().stream().filter(a -> a.getInjuriesCount() > 0).count();
        long fatalities = in.accidents().stream().filter(a -> a.getFatalitiesCount() > 0).count();
        int score = (int) Math.max(0, 100 - (20L * n + 15L * injuries + 50L * fatalities));
        String detail = n == 0 ? "No accidents in the period"
                : n + " accident(s), " + injuries + " with injuries, " + fatalities
                + " with fatalities (not fault-adjusted)";
        return Scored.of(score, detail);
    }

    private static Scored fuel(Input in) {
        Map<UUID, double[]> perVehicle = new HashMap<>();   // [litres, km, count]
        for (Trip t : in.trips()) {
            Integer km = t.getDistanceKm();
            if (!"COMPLETED".equals(t.getStatus()) || t.getFuelUsedLitres() == null || km == null || km <= 0
                    || t.getFuelUsedLitres().signum() <= 0) continue;
            double[] a = perVehicle.computeIfAbsent(t.getVehicleId(), k -> new double[3]);
            a[0] += t.getFuelUsedLitres().doubleValue();
            a[1] += km;
            a[2] += 1;
        }
        int usable = perVehicle.values().stream().mapToInt(a -> (int) a[2]).sum();
        if (usable < MIN_SAMPLE) {
            return Scored.noData("Fewer than " + MIN_SAMPLE + " trips with fuel and distance recorded");
        }

        double ratioKmSum = 0;
        double kmSum = 0;
        double litres = 0;
        double allKm = 0;
        for (Map.Entry<UUID, double[]> e : perVehicle.entrySet()) {
            double[] a = e.getValue();
            litres += a[0];
            allKm += a[1];
            Double baseline = in.fuelBaselines().get(e.getKey());
            if (baseline == null || baseline <= 0) continue;
            double driverL100 = a[0] / a[1] * 100.0;
            ratioKmSum += (driverL100 / baseline) * a[1];
            kmSum += a[1];
        }
        if (kmSum == 0) {
            return Scored.noData("No fuel-economy norm yet for the vehicles driven (needs at least "
                    + MIN_BASELINE_TRIPS + " trips with fuel data per vehicle)");
        }
        double ratio = ratioKmSum / kmSum;
        int score = (int) Math.round(clamp(100 - Math.max(0, ratio - 1.0) * 250.0, 0, 100));
        return Scored.of(score, String.format("%.1f L/100 km — %.0f%% of the vehicles' own norm (%d trips, %,.0f km)",
                litres / allKm * 100.0, ratio * 100.0, usable, allKm));
    }

    private static Scored trips(Input in) {
        List<Trip> considered = in.trips().stream()
                .filter(t -> "COMPLETED".equals(t.getStatus())
                        || ("ACTIVE".equals(t.getStatus()) && t.isLongRunningAlertSent()))
                .toList();
        if (considered.size() < MIN_SAMPLE) {
            return Scored.noData("Fewer than " + MIN_SAMPLE + " finished trips linked to this driver");
        }
        long compliant = considered.stream()
                .filter(t -> "COMPLETED".equals(t.getStatus()) && !t.isLongRunningAlertSent()
                        && t.getEndOdometer() != null && t.getEndAt() != null)
                .count();
        return Scored.of(pct((int) compliant, considered.size()), compliant + " of " + considered.size()
                + " trips were closed properly (end odometer and time recorded, not left running)");
    }

    private static Scored compliance(Input in) {
        List<ComplianceItemResponse> items = complianceItems(in.driver(), in.today());
        int worst = items.stream().mapToInt(i -> complianceScore(i.status())).min().orElse(100);
        String detail = items.stream().map(i -> i.label() + ": " + i.status())
                .reduce((a, b) -> a + ", " + b).orElse("No documents to check");
        return Scored.of(worst, detail);
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private static ComplianceItemResponse expiryItem(String key, String label, LocalDate expiry, LocalDate today) {
        if (expiry == null) {
            return new ComplianceItemResponse(key, label, ComplianceStatus.MISSING, null, null, null,
                    "No expiry date recorded");
        }
        long days = ChronoUnit.DAYS.between(today, expiry);
        // Same 30-day window the rest of Fleet uses for "expiring soon".
        ComplianceStatus status = days < 0 ? ComplianceStatus.EXPIRED
                : expiry.isBefore(today.plusDays(30)) ? ComplianceStatus.EXPIRING_SOON
                : ComplianceStatus.VALID;
        return new ComplianceItemResponse(key, label, status, expiry, days, null, null);
    }

    private static int complianceScore(ComplianceStatus s) {
        return switch (s) {
            case EXPIRED -> 0;
            case MISSING -> 50;
            case EXPIRING_SOON -> 70;
            default -> 100;
        };
    }

    private static String band(Double overall, DriverScoreSettings s) {
        if (overall == null) return null;
        if (overall >= s.getExcellentThreshold()) return "EXCELLENT";
        if (overall >= s.getGoodThreshold()) return "GOOD";
        if (overall >= s.getFairThreshold()) return "FAIR";
        return "POOR";
    }

    private static int pct(int part, int whole) {
        return whole == 0 ? 0 : (int) Math.round(part * 100.0 / whole);
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    /** Orders leaderboard rows best first; drivers with no score sort last. */
    static final Comparator<Double> BEST_FIRST_NULLS_LAST =
            Comparator.nullsLast(Comparator.<Double>naturalOrder().reversed());
}