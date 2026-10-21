package za.co.handyflow.platform.fleet.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.fleet.domain.model.Accident;
import za.co.handyflow.platform.fleet.domain.model.Driver;
import za.co.handyflow.platform.fleet.domain.model.DriverScoreSettings;
import za.co.handyflow.platform.fleet.domain.model.Inspection;
import za.co.handyflow.platform.fleet.domain.model.InspectionStatus;
import za.co.handyflow.platform.fleet.domain.model.InspectionVerdict;
import za.co.handyflow.platform.fleet.domain.model.TelematicsEvent;
import za.co.handyflow.platform.fleet.domain.model.Trip;
import za.co.handyflow.platform.fleet.domain.model.Vehicle;
import za.co.handyflow.platform.fleet.domain.repository.AccidentRepository;
import za.co.handyflow.platform.fleet.domain.repository.DriverRepository;
import za.co.handyflow.platform.fleet.domain.repository.DriverScoreSettingsRepository;
import za.co.handyflow.platform.fleet.domain.repository.InspectionRepository;
import za.co.handyflow.platform.fleet.domain.repository.TelematicsDeviceRepository;
import za.co.handyflow.platform.fleet.domain.repository.TelematicsEventRepository;
import za.co.handyflow.platform.fleet.domain.repository.TripRepository;
import za.co.handyflow.platform.fleet.domain.repository.VehicleRepository;
import za.co.handyflow.platform.fleet.dto.*;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Driver 360 and the Driver Safety &amp; Performance Score.
 * <ul>
 *   <li>{@link #getProfile} — one driver: compliance, score with transparent components,
 *       activity, accidents and damage cost.</li>
 *   <li>{@link #getScores} — leaderboard of every active driver, computed from a handful of
 *       tenant-wide queries (not one query set per driver).</li>
 *   <li>settings — per-tenant weights, lookback and band thresholds.</li>
 * </ul>
 * Only trips, accidents and inspections linked to the driver by id count; free-text
 * driver names are not matched.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DriverScoreService {

    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");
    private static final int MAX_LEADERBOARD_DRIVERS = 1000;

    private final DriverRepository driverRepository;
    private final VehicleRepository vehicleRepository;
    private final TripRepository tripRepository;
    private final AccidentRepository accidentRepository;
    private final InspectionRepository inspectionRepository;
    private final DriverScoreSettingsRepository settingsRepository;
    private final TelematicsEventRepository telematicsEventRepository;
    private final TelematicsDeviceRepository telematicsDeviceRepository;

    private record Period(LocalDate from, LocalDate to, Instant fromInstant, Instant toInstant, Instant baselineFrom) {}

    // ── Settings ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public DriverScoreSettingsResponse getSettings(TenantId tenantId) {
        return settingsRepository.findByTenantId(tenantId)
                .map(s -> toSettingsResponse(s, false))
                .orElseGet(() -> toSettingsResponse(DriverScoreSettings.defaults(tenantId), true));
    }

    @Transactional
    public DriverScoreSettingsResponse updateSettings(TenantId tenantId, UUID userId,
                                                      UpdateDriverScoreSettingsRequest req) {
        DriverScoreSettings s = settingsRepository.findByTenantId(tenantId)
                .orElseGet(() -> DriverScoreSettings.defaults(tenantId));
        s.apply(req.weightSafety(), req.weightInspection(), req.weightAccidents(), req.weightFuel(),
                req.weightTrips(), req.weightCompliance(), req.lookbackDays(),
                req.excellentThreshold(), req.goodThreshold(), req.fairThreshold(), userId);
        settingsRepository.save(s);
        log.info("Driver score settings updated tenant={} by={}", tenantId, userId);
        return toSettingsResponse(s, false);
    }

    // ── Driver 360 ───────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public DriverProfileResponse getProfile(TenantId tenantId, UUID driverId, LocalDate from, LocalDate to) {
        Driver driver = driverRepository.findActiveById(tenantId, driverId)
                .orElseThrow(() -> new ResourceNotFoundException("Driver", driverId.toString()));
        DriverScoreSettings settings = effectiveSettings(tenantId);
        Period p = resolvePeriod(from, to, settings);

        List<Trip> trips = tripRepository.findByDriverInRange(tenantId, driverId, p.fromInstant(), p.toInstant());
        List<Accident> accidents = accidentRepository.findByDriverInRange(tenantId, driverId, p.fromInstant(), p.toInstant());
        List<Inspection> inspections = inspectionRepository.findCompletedByDriverInRange(
                tenantId, driverId, InspectionStatus.COMPLETED, p.fromInstant(), p.toInstant());

        List<UUID> vehicleIds = trips.stream().map(Trip::getVehicleId).distinct().toList();
        Map<UUID, Double> baselines = vehicleIds.isEmpty() ? Map.of()
                : DriverScoreCalculator.fuelBaselines(tripRepository.findFuelBaselineTrips(
                tenantId, vehicleIds, p.baselineFrom(), p.toInstant()));

        List<TelematicsEvent> telematics = telematicsEventRepository.findByDriverInRange(
                tenantId, driverId, p.fromInstant(), p.toInstant());
        Set<UUID> tracked = new HashSet<>(telematicsDeviceRepository.findTrackedVehicleIds(tenantId));

        LocalDate today = LocalDate.now(SAST);
        DriverScoreCalculator.Result result = DriverScoreCalculator.calculate(new DriverScoreCalculator.Input(
                driver, trips, accidents, inspections, baselines, today, settings, telematics, tracked));

        return new DriverProfileResponse(
                toDriverResponse(driver),
                DriverScoreCalculator.complianceItems(driver, today),
                toScoreResponse(result, p),
                toActivity(trips, inspections, vehicleIds, tenantId),
                toAccidentSummary(accidents));
    }

    // ── Leaderboard ──────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<DriverScoreSummaryResponse> getScores(TenantId tenantId, LocalDate from, LocalDate to) {
        DriverScoreSettings settings = effectiveSettings(tenantId);
        Period p = resolvePeriod(from, to, settings);

        List<Driver> drivers = driverRepository
                .findAllActive(tenantId, PageRequest.of(0, MAX_LEADERBOARD_DRIVERS)).getContent().stream()
                .filter(d -> "ACTIVE".equals(d.getStatus()))
                .toList();

        // A few tenant-wide queries, then group in memory.
        List<Trip> allTrips = tripRepository.findByTenantInRange(tenantId, p.baselineFrom(), p.toInstant());
        Map<UUID, Double> baselines = DriverScoreCalculator.fuelBaselines(allTrips);
        Map<UUID, List<Trip>> tripsByDriver = allTrips.stream()
                .filter(t -> t.getDriverId() != null && !t.getStartAt().isBefore(p.fromInstant()))
                .collect(Collectors.groupingBy(Trip::getDriverId));
        Map<UUID, List<Accident>> accidentsByDriver = accidentRepository
                .findWithDriverInRange(tenantId, p.fromInstant(), p.toInstant()).stream()
                .collect(Collectors.groupingBy(Accident::getDriverId));
        Map<UUID, List<Inspection>> inspectionsByDriver = inspectionRepository
                .findCompletedWithDriverInRange(tenantId, InspectionStatus.COMPLETED, p.fromInstant(), p.toInstant())
                .stream().collect(Collectors.groupingBy(Inspection::getDriverId));

        Map<UUID, List<TelematicsEvent>> telematicsByDriver = telematicsEventRepository
                .findWithDriverInRange(tenantId, p.fromInstant(), p.toInstant()).stream()
                .collect(Collectors.groupingBy(TelematicsEvent::getDriverId));
        Set<UUID> tracked = new HashSet<>(telematicsDeviceRepository.findTrackedVehicleIds(tenantId));

        LocalDate today = LocalDate.now(SAST);
        return drivers.stream()
                .map(d -> {
                    List<Trip> trips = tripsByDriver.getOrDefault(d.getId(), List.of());
                    DriverScoreCalculator.Result r = DriverScoreCalculator.calculate(new DriverScoreCalculator.Input(
                            d, trips, accidentsByDriver.getOrDefault(d.getId(), List.of()),
                            inspectionsByDriver.getOrDefault(d.getId(), List.of()), baselines, today, settings,
                            telematicsByDriver.getOrDefault(d.getId(), List.of()), tracked));
                    return new DriverScoreSummaryResponse(d.getId(), d.getFullName(), r.overall(), r.band(),
                            r.coverage(), r.reliable(), trips.size());
                })
                .sorted(Comparator.comparing(DriverScoreSummaryResponse::overall,
                                DriverScoreCalculator.BEST_FIRST_NULLS_LAST)
                        .thenComparing(DriverScoreSummaryResponse::driverName))
                .toList();
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private DriverScoreSettings effectiveSettings(TenantId tenantId) {
        return settingsRepository.findByTenantId(tenantId)
                .orElseGet(() -> DriverScoreSettings.defaults(tenantId));
    }

    /** Defaults to the last {@code lookbackDays} days ending today (SAST). */
    private Period resolvePeriod(LocalDate from, LocalDate to, DriverScoreSettings settings) {
        LocalDate end = to != null ? to : LocalDate.now(SAST);
        LocalDate start = from != null ? from : end.minusDays(settings.getLookbackDays() - 1L);
        if (start.isAfter(end)) {
            throw new IllegalArgumentException("'from' cannot be after 'to'");
        }
        long span = ChronoUnit.DAYS.between(start, end) + 1;
        if (span > 730) {
            throw new IllegalArgumentException("The period cannot exceed 730 days");
        }
        return new Period(start, end,
                start.atStartOfDay(SAST).toInstant(),
                end.plusDays(1).atStartOfDay(SAST).toInstant(),
                start.minusDays(span).atStartOfDay(SAST).toInstant());   // fuel norms use twice the period
    }

    private DriverScoreSettingsResponse toSettingsResponse(DriverScoreSettings s, boolean usingDefaults) {
        return new DriverScoreSettingsResponse(s.getWeightSafety(), s.getWeightInspection(), s.getWeightAccidents(),
                s.getWeightFuel(), s.getWeightTrips(), s.getWeightCompliance(), s.getLookbackDays(),
                s.getExcellentThreshold(), s.getGoodThreshold(), s.getFairThreshold(), usingDefaults,
                usingDefaults ? null : s.getUpdatedAt());
    }

    private DriverScoreResponse toScoreResponse(DriverScoreCalculator.Result r, Period p) {
        List<ScoreComponentResponse> components = r.components().stream()
                .map(c -> new ScoreComponentResponse(c.component().name(), c.component().label(), c.weight(),
                        c.effectiveWeight(), c.score(), c.score() != null ? "SCORED" : "NO_DATA", c.detail()))
                .toList();
        return new DriverScoreResponse(r.overall(), r.band(), r.coverage(), r.reliable(),
                p.from(), p.to(), components);
    }

    private DriverActivityResponse toActivity(List<Trip> trips, List<Inspection> inspections,
                                              List<UUID> vehicleIds, TenantId tenantId) {
        List<Trip> completed = trips.stream().filter(t -> "COMPLETED".equals(t.getStatus())).toList();
        int totalKm = 0, businessKm = 0, privateKm = 0;
        BigDecimal litres = BigDecimal.ZERO;
        BigDecimal fuelTripLitres = BigDecimal.ZERO;
        int fuelTripKm = 0;
        for (Trip t : completed) {
            Integer km = t.getDistanceKm();
            if (km != null) {
                totalKm += km;
                if ("PRIVATE".equalsIgnoreCase(t.getTripType())) privateKm += km; else businessKm += km;
            }
            if (t.getFuelUsedLitres() != null && t.getFuelUsedLitres().signum() > 0) {
                litres = litres.add(t.getFuelUsedLitres());
                if (km != null && km > 0) {
                    fuelTripLitres = fuelTripLitres.add(t.getFuelUsedLitres());
                    fuelTripKm += km;
                }
            }
        }
        BigDecimal l100 = fuelTripKm > 0
                ? fuelTripLitres.multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(fuelTripKm), 1, RoundingMode.HALF_UP)
                : null;
        int failed = (int) inspections.stream().filter(i -> i.getVerdict() == InspectionVerdict.FAIL).count();
        List<String> registrations = vehicleIds.stream()
                .map(id -> vehicleRepository.findActiveById(tenantId, id).map(Vehicle::getRegistration).orElse(null))
                .filter(r -> r != null).sorted().toList();

        return new DriverActivityResponse(trips.size(), completed.size(), totalKm, businessKm, privateKm,
                litres, l100, inspections.size(), failed, registrations);
    }

    private DriverAccidentSummaryResponse toAccidentSummary(List<Accident> accidents) {
        BigDecimal repair = accidents.stream().map(Accident::getRepairCost).filter(c -> c != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal excess = accidents.stream().map(Accident::getClaimExcess).filter(c -> c != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<DriverAccidentSummaryResponse.Item> items = accidents.stream()
                .map(a -> new DriverAccidentSummaryResponse.Item(a.getId(), a.getReferenceNumber(),
                        a.getOccurredAt(), a.getVehicleId(), a.getStatus().name()))
                .toList();
        return new DriverAccidentSummaryResponse(accidents.size(),
                (int) accidents.stream().filter(a -> a.getInjuriesCount() > 0).count(),
                (int) accidents.stream().filter(a -> a.getFatalitiesCount() > 0).count(),
                repair, excess, items);
    }

    private DriverResponse toDriverResponse(Driver d) {
        return new DriverResponse(
                d.getId(), d.getFirstName(), d.getLastName(), d.getPhone(), d.getEmail(), d.getIdNumber(),
                d.getLicenseNumber(), d.getLicenseCode(), d.getLicenseExpiry(), d.isLicenseExpiringSoon(),
                d.isPrdpRequired(), d.getPrdpNumber(), d.getPrdpCategory(), d.getPrdpExpiry(), d.isPrdpExpiringSoon(),
                d.getStatus(), d.getNotes(), d.getCreatedAt(), d.getDepartment());
    }
}