package za.co.handyflow.platform.fleet.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.fleet.application.internal.FuelAnalyticsCalculator.Indicator;
import za.co.handyflow.platform.fleet.application.internal.FuelAnalyticsCalculator.Segment;
import za.co.handyflow.platform.fleet.application.internal.FuelAnalyticsCalculator.SegmentStatus;
import za.co.handyflow.platform.fleet.domain.model.FuelFillup;
import za.co.handyflow.platform.fleet.domain.model.Vehicle;
import za.co.handyflow.platform.fleet.domain.model.VehicleFuelSettings;
import za.co.handyflow.platform.fleet.domain.repository.FuelFillupRepository;
import za.co.handyflow.platform.fleet.domain.repository.VehicleFuelSettingsRepository;
import za.co.handyflow.platform.fleet.domain.repository.VehicleRepository;
import za.co.handyflow.platform.fleet.dto.*;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Fuel analytics: consumption (full-tank-to-full-tank), abnormal-consumption flags, theft /
 * data-quality indicators and station price analysis. The maths lives in
 * {@link FuelAnalyticsCalculator}; this service loads the data and shapes the responses.
 * <p>
 * Notes:
 * <ul>
 *   <li>The period defaults to the last 90 days and is capped at 366. Baselines look further back
 *       ({@value #BASELINE_LOOKBACK_DAYS} days before the period starts) so even a short period
 *       has history to compare with.</li>
 *   <li>Results are computed on demand from the fill-up log, never stored — correcting a fill-up
 *       corrects the analytics.</li>
 *   <li>Quality in, quality out: the figures are only as good as the odometer readings and the
 *       "full tank" flags on fill-ups. Fill-ups created from internal tank dispatches are not full-tank
 *       events and so act as partial fills.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FuelAnalyticsService {

    static final int BASELINE_LOOKBACK_DAYS = 240;
    private static final int DEFAULT_PERIOD_DAYS = 90;
    private static final int MAX_PERIOD_DAYS = 366;
    private static final int MAX_VEHICLES = 1000;
    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");

    private final VehicleRepository vehicleRepository;
    private final FuelFillupRepository fuelFillupRepository;
    private final VehicleFuelSettingsRepository settingsRepository;

    private record Period(LocalDate from, LocalDate to) {}

    /** Everything computed for one vehicle over one period. */
    private record VehicleAnalysis(List<Segment> windowSegments, List<Indicator> indicators,
                                   FuelPeriodSummary summary, Segment latestAssessed) {}

    // ── Per vehicle ──────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public VehicleFuelAnalyticsResponse getVehicleAnalytics(TenantId tenantId, UUID vehicleId,
                                                            LocalDate from, LocalDate to) {
        Vehicle vehicle = findVehicle(tenantId, vehicleId);
        Period p = resolvePeriod(from, to);
        VehicleFuelSettings settings = settingsRepository.findByTenantIdAndVehicleId(tenantId, vehicleId).orElse(null);

        List<FuelFillup> fills = fuelFillupRepository.findByVehicleSinceOrdered(vehicleId,
                p.from().minusDays(BASELINE_LOOKBACK_DAYS));
        Map<String, Long> receiptCounts = receiptCountsFor(tenantId, fills, p);

        VehicleAnalysis a = analyse(vehicle, fills, receiptCounts, settings, p);
        String reg = vehicle.getRegistration();
        return new VehicleFuelAnalyticsResponse(vehicleId, reg, vehicle.getFuelType(), vehicle.getTankCapacityLitres(),
                p.from(), p.to(), toSettingsResponse(settings), a.summary(),
                a.windowSegments().stream().sorted(Comparator.comparing(Segment::endDate).reversed())
                        .map(this::toSegmentResponse).toList(),
                a.indicators().stream().sorted(Comparator.comparing((Indicator i) -> i.fillup().getFilledAt()).reversed())
                        .map(i -> toIndicatorResponse(i, reg)).toList());
    }

    @Transactional
    public FuelSettingsResponse updateSettings(TenantId tenantId, UUID vehicleId, UUID userId,
                                               UpdateFuelSettingsRequest req) {
        findVehicle(tenantId, vehicleId);
        VehicleFuelSettings s = settingsRepository.findByTenantIdAndVehicleId(tenantId, vehicleId)
                .orElseGet(() -> VehicleFuelSettings.create(tenantId, vehicleId));
        s.apply(req.expectedLitresPer100Km(), req.tolerancePercent(), userId);
        settingsRepository.save(s);
        return toSettingsResponse(s);
    }

    // ── Fleet-wide ───────────────────────────────────────────────────────

    /** One row per vehicle with fuel activity, thirstiest (relative to its own baseline) first. */
    @Transactional(readOnly = true)
    public List<FleetFuelSummaryRow> getFleetSummary(TenantId tenantId, LocalDate from, LocalDate to) {
        Period p = resolvePeriod(from, to);
        TenantData d = loadTenant(tenantId, p);

        List<FleetFuelSummaryRow> rows = new ArrayList<>();
        for (Vehicle v : d.vehicles()) {
            List<FuelFillup> fills = d.fillsByVehicle().get(v.getId());
            if (fills == null) continue;
            VehicleAnalysis a = analyse(v, fills, d.receiptCounts(), d.settings().get(v.getId()), p);
            if (a.summary().fillups() == 0 && a.windowSegments().isEmpty()) continue;
            Segment latest = a.latestAssessed();
            rows.add(new FleetFuelSummaryRow(v.getId(), v.getRegistration(), v.getMake(), v.getModel(),
                    v.getFuelType(), a.summary(),
                    latest != null && latest.baseline() != null ? round(latest.baseline(), 1) : null,
                    latest != null && latest.deviationPercent() != null ? round(latest.deviationPercent(), 1) : null));
        }
        rows.sort(Comparator.comparing(FleetFuelSummaryRow::latestDeviationPercent,
                Comparator.nullsLast(Comparator.<Double>naturalOrder().reversed())));
        return rows;
    }

    /** Abnormal consumption and theft / data-quality indicators across the whole fleet, newest first. */
    @Transactional(readOnly = true)
    public List<FuelAlertResponse> getAlerts(TenantId tenantId, LocalDate from, LocalDate to) {
        Period p = resolvePeriod(from, to);
        TenantData d = loadTenant(tenantId, p);

        List<FuelAlertResponse> alerts = new ArrayList<>();
        for (Vehicle v : d.vehicles()) {
            List<FuelFillup> fills = d.fillsByVehicle().get(v.getId());
            if (fills == null) continue;
            VehicleAnalysis a = analyse(v, fills, d.receiptCounts(), d.settings().get(v.getId()), p);
            for (Segment s : a.windowSegments()) {
                if (!s.isAbnormal()) continue;
                alerts.add(new FuelAlertResponse("CONSUMPTION", s.status().name(), severityOf(s), v.getId(),
                        v.getRegistration(), s.endDate(), s.describe()));
            }
            for (Indicator i : a.indicators()) {
                alerts.add(new FuelAlertResponse("INDICATOR", i.type().name(), i.severity().name(), v.getId(),
                        v.getRegistration(), i.fillup().getFilledAt(), i.message()));
            }
        }
        alerts.sort(Comparator.comparing(FuelAlertResponse::date).reversed());
        return alerts;
    }

    /** Spend and average price per station and fuel type, with the premium over the tenant's average for that fuel. */
    @Transactional(readOnly = true)
    public List<FuelStationSummaryRow> getStations(TenantId tenantId, LocalDate from, LocalDate to) {
        Period p = resolvePeriod(from, to);
        List<FuelFillup> fills = fuelFillupRepository.findByTenantInRange(tenantId.getValue(), p.from(), p.to());
        Map<UUID, String> fuelTypes = vehicleRepository
                .findAllActive(tenantId, PageRequest.of(0, MAX_VEHICLES)).getContent().stream()
                .collect(Collectors.toMap(Vehicle::getId, v -> v.getFuelType() != null ? v.getFuelType() : "UNKNOWN",
                        (a, b) -> a));

        Map<String, Acc> perFuel = new HashMap<>();
        Map<String, Acc> perStation = new LinkedHashMap<>();
        for (FuelFillup f : fills) {
            String fuel = fuelTypes.getOrDefault(f.getVehicleId(), "UNKNOWN");
            String stationDisplay = stationName(f.getStation());
            perFuel.computeIfAbsent(fuel, k -> new Acc(fuel, fuel)).add(f);
            perStation.computeIfAbsent(stationDisplay.toLowerCase() + "|" + fuel,
                    k -> new Acc(stationDisplay, fuel)).add(f);
        }

        List<FuelStationSummaryRow> rows = new ArrayList<>();
        for (Acc s : perStation.values()) {
            BigDecimal avg = s.avgPrice();
            BigDecimal tenantAvg = perFuel.get(s.fuelType).avgPrice();
            Double premium = avg != null && tenantAvg != null && tenantAvg.signum() > 0
                    ? round(avg.subtract(tenantAvg).doubleValue() / tenantAvg.doubleValue() * 100.0, 1) : null;
            rows.add(new FuelStationSummaryRow(s.name, s.fuelType, s.count,
                    BigDecimal.valueOf(s.litres).setScale(1, RoundingMode.HALF_UP),
                    s.spend.setScale(2, RoundingMode.HALF_UP), avg, tenantAvg, premium));
        }
        rows.sort(Comparator.comparing(FuelStationSummaryRow::totalSpend).reversed());
        return rows;
    }

    // ── Daily anomaly sweep ──────────────────────────────────────────────

    /**
     * Anomalies that involve any of the given (newly recorded) fill-ups: an abnormal segment
     * ending at one of them, or an indicator on one of them. One readable line each; empty if all is well.
     */
    @Transactional(readOnly = true)
    public List<String> detectAnomalies(TenantId tenantId, Vehicle vehicle, Set<UUID> newFillIds) {
        LocalDate today = LocalDate.now(SAST);
        List<FuelFillup> fills = fuelFillupRepository.findByVehicleSinceOrdered(vehicle.getId(),
                today.minusDays(BASELINE_LOOKBACK_DAYS + 30L));
        if (fills.isEmpty()) return List.of();

        LocalDate first = fills.get(0).getFilledAt();
        Period p = new Period(first, today);
        VehicleFuelSettings settings = settingsRepository.findByTenantIdAndVehicleId(tenantId, vehicle.getId()).orElse(null);
        VehicleAnalysis a = analyse(vehicle, fills, receiptCountsFor(tenantId, fills, p), settings, p);

        List<String> lines = new ArrayList<>();
        for (Segment s : a.windowSegments()) {
            if (s.isAbnormal() && newFillIds.contains(s.end().getId())) lines.add(s.describe());
        }
        for (Indicator i : a.indicators()) {
            if (newFillIds.contains(i.fillup().getId())) {
                lines.add(i.type() + " (" + i.fillup().getFilledAt() + "): " + i.message());
            }
        }
        return lines;
    }

    // ── Core ─────────────────────────────────────────────────────────────

    private VehicleAnalysis analyse(Vehicle vehicle, List<FuelFillup> fills, Map<String, Long> receiptCounts,
                                    VehicleFuelSettings settings, Period p) {
        Double expected = settings != null && settings.getExpectedLitresPer100Km() != null
                ? settings.getExpectedLitresPer100Km().doubleValue() : null;
        int tolerance = effectiveTolerance(settings);

        List<Segment> all = FuelAnalyticsCalculator.segments(fills, expected, tolerance);
        List<Segment> window = all.stream()
                .filter(s -> !s.endDate().isBefore(p.from()) && !s.endDate().isAfter(p.to())).toList();
        List<Indicator> indicators = FuelAnalyticsCalculator.indicators(fills, vehicle.getTankCapacityLitres(),
                receiptCounts, p.from(), p.to());

        List<FuelFillup> windowFills = fills.stream()
                .filter(f -> !f.getFilledAt().isBefore(p.from()) && !f.getFilledAt().isAfter(p.to())).toList();
        BigDecimal litres = windowFills.stream().map(FuelFillup::getLitres).filter(l -> l != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal spend = windowFills.stream().map(FuelAnalyticsCalculator::costOf).filter(c -> c != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<Segment> assessable = window.stream().filter(Segment::isAssessable).toList();
        int km = assessable.stream().mapToInt(Segment::km).sum();
        double segLitres = assessable.stream().mapToDouble(Segment::litres).sum();
        List<Segment> costed = assessable.stream().filter(s -> s.cost() != null).toList();
        int costedKm = costed.stream().mapToInt(Segment::km).sum();
        BigDecimal costedSpend = costed.stream().map(Segment::cost).reduce(BigDecimal.ZERO, BigDecimal::add);

        FuelPeriodSummary summary = new FuelPeriodSummary(windowFills.size(),
                litres.setScale(1, RoundingMode.HALF_UP), spend.setScale(2, RoundingMode.HALF_UP), km,
                km > 0 ? round(segLitres / km * 100.0, 1) : null,
                segLitres > 0 ? round(km / segLitres, 2) : null,
                costedKm > 0 ? costedSpend.divide(BigDecimal.valueOf(costedKm), 3, RoundingMode.HALF_UP) : null,
                (int) window.stream().filter(Segment::isAbnormal).count(), indicators.size());

        Segment latest = null;
        for (int i = window.size() - 1; i >= 0; i--) {
            if (window.get(i).deviationPercent() != null) { latest = window.get(i); break; }
        }
        return new VehicleAnalysis(window, indicators, summary, latest);
    }

    private record TenantData(List<Vehicle> vehicles, Map<UUID, List<FuelFillup>> fillsByVehicle,
                              Map<UUID, VehicleFuelSettings> settings, Map<String, Long> receiptCounts) {}

    private TenantData loadTenant(TenantId tenantId, Period p) {
        List<Vehicle> vehicles = vehicleRepository.findAllActive(tenantId, PageRequest.of(0, MAX_VEHICLES)).getContent();
        List<FuelFillup> fills = fuelFillupRepository.findByTenantInRange(tenantId.getValue(),
                p.from().minusDays(BASELINE_LOOKBACK_DAYS), p.to());
        Map<UUID, VehicleFuelSettings> settings = settingsRepository.findAllForTenant(tenantId).stream()
                .collect(Collectors.toMap(VehicleFuelSettings::getVehicleId, s -> s, (a, b) -> a));
        return new TenantData(vehicles, FuelAnalyticsCalculator.byVehicle(fills), settings,
                FuelAnalyticsCalculator.receiptCounts(fills));
    }

    /** Receipt counts across the tenant for the references used by this vehicle's fill-ups in the period. */
    private Map<String, Long> receiptCountsFor(TenantId tenantId, List<FuelFillup> vehicleFills, Period p) {
        Set<String> refs = new HashSet<>();
        for (FuelFillup f : vehicleFills) {
            if (f.getFilledAt().isBefore(p.from()) || f.getFilledAt().isAfter(p.to())) continue;
            String ref = FuelAnalyticsCalculator.normaliseReceipt(f.getReceiptRef());
            if (ref != null) refs.add(ref);
        }
        if (refs.isEmpty()) return Map.of();
        return FuelAnalyticsCalculator.receiptCounts(
                fuelFillupRepository.findByTenantAndReceiptRefs(tenantId.getValue(), refs));
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private Vehicle findVehicle(TenantId tenantId, UUID id) {
        return vehicleRepository.findActiveById(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle", id.toString()));
    }

    private Period resolvePeriod(LocalDate from, LocalDate to) {
        LocalDate end = to != null ? to : LocalDate.now(SAST);
        LocalDate start = from != null ? from : end.minusDays(DEFAULT_PERIOD_DAYS - 1L);
        if (start.isAfter(end)) throw new IllegalArgumentException("'from' cannot be after 'to'");
        if (ChronoUnit.DAYS.between(start, end) + 1 > MAX_PERIOD_DAYS) {
            throw new IllegalArgumentException("The period cannot exceed " + MAX_PERIOD_DAYS + " days");
        }
        return new Period(start, end);
    }

    private int effectiveTolerance(VehicleFuelSettings s) {
        return s != null && s.getTolerancePercent() != null
                ? s.getTolerancePercent() : FuelAnalyticsCalculator.DEFAULT_TOLERANCE_PERCENT;
    }

    private String severityOf(Segment s) {
        if (s.status() == SegmentStatus.HIGH_CONSUMPTION && s.deviationPercent() != null && s.deviationPercent() >= 50) {
            return "ALERT";
        }
        return "WARNING";
    }

    private String stationName(String raw) {
        if (raw == null) return "(no station)";
        String t = raw.trim().replaceAll("\\s+", " ");
        return t.isEmpty() ? "(no station)" : t;
    }

    private static double round(double v, int places) {
        double f = Math.pow(10, places);
        return Math.round(v * f) / f;
    }

    /** Running totals for one station / fuel type (or, keyed by fuel only, the tenant-wide average). */
    private static final class Acc {
        final String name;
        final String fuelType;
        int count;
        double litres;
        BigDecimal spend = BigDecimal.ZERO;
        BigDecimal pricedLitres = BigDecimal.ZERO;
        BigDecimal pricedSpend = BigDecimal.ZERO;

        Acc(String name, String fuelType) { this.name = name; this.fuelType = fuelType; }

        void add(FuelFillup f) {
            count++;
            if (f.getLitres() != null) litres += f.getLitres().doubleValue();
            BigDecimal cost = FuelAnalyticsCalculator.costOf(f);
            if (cost != null) {
                spend = spend.add(cost);
                if (f.getLitres() != null && f.getLitres().signum() > 0) {
                    pricedLitres = pricedLitres.add(f.getLitres());
                    pricedSpend = pricedSpend.add(cost);
                }
            }
        }

        /** Spend over litres for the fill-ups whose cost is known — so unpriced fill-ups don't drag the average down. */
        BigDecimal avgPrice() {
            return pricedLitres.signum() > 0 ? pricedSpend.divide(pricedLitres, 3, RoundingMode.HALF_UP) : null;
        }
    }

    // ── Mapping ──────────────────────────────────────────────────────────

    private FuelSettingsResponse toSettingsResponse(VehicleFuelSettings s) {
        return new FuelSettingsResponse(s != null ? s.getExpectedLitresPer100Km() : null, effectiveTolerance(s),
                s == null || s.getTolerancePercent() == null);
    }

    private FuelSegmentResponse toSegmentResponse(Segment s) {
        BigDecimal costPerKm = s.cost() != null
                ? s.cost().divide(BigDecimal.valueOf(s.km()), 3, RoundingMode.HALF_UP) : null;
        return new FuelSegmentResponse(s.startDate(), s.endDate(), s.start().getOdometerAtFillup(),
                s.end().getOdometerAtFillup(), s.km(), round(s.litres(), 1), s.partialFills(),
                round(s.litresPer100Km(), 1), round(s.kmPerLitre(), 2), s.cost(), costPerKm,
                s.baseline() != null ? round(s.baseline(), 1) : null, s.baselineSource().name(),
                s.deviationPercent() != null ? round(s.deviationPercent(), 1) : null, s.status().name());
    }

    private FuelIndicatorResponse toIndicatorResponse(Indicator i, String registration) {
        return new FuelIndicatorResponse(i.type().name(), i.severity().name(), i.fillup().getId(),
                i.fillup().getVehicleId(), registration, i.fillup().getFilledAt(), i.message());
    }
}