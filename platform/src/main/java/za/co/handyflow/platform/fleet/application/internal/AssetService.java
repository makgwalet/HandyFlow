package za.co.handyflow.platform.fleet.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.fleet.domain.model.AssetAttachment;
import za.co.handyflow.platform.fleet.domain.model.AssetClass;
import za.co.handyflow.platform.fleet.domain.model.AssetMeterReading;
import za.co.handyflow.platform.fleet.domain.model.AssetProfile;
import za.co.handyflow.platform.fleet.domain.model.FuelFillup;
import za.co.handyflow.platform.fleet.domain.model.Vehicle;
import za.co.handyflow.platform.fleet.domain.model.VehicleStatus;
import za.co.handyflow.platform.fleet.domain.repository.AssetAttachmentRepository;
import za.co.handyflow.platform.fleet.domain.repository.AssetMeterReadingRepository;
import za.co.handyflow.platform.fleet.domain.repository.AssetProfileRepository;
import za.co.handyflow.platform.fleet.domain.repository.FuelFillupRepository;
import za.co.handyflow.platform.fleet.domain.repository.VehicleRepository;
import za.co.handyflow.platform.fleet.domain.repository.VehicleServiceRepository;
import za.co.handyflow.platform.fleet.dto.*;
import za.co.handyflow.platform.notifications.application.NotificationRequest;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.TenantAdminRecipients;
import za.co.handyflow.platform.notifications.application.internal.NotificationService;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Fleet assets beyond road vehicles: tractors, plant, forklifts, trailers, specialised equipment.
 * <p>
 * A vehicle with no {@link AssetProfile} is a plain road vehicle; configuring a profile makes it an
 * asset of a class and adds what that class needs. Nothing here changes the vehicle record itself:
 * <ul>
 *   <li><b>Hour meters</b> — engine hours stay on {@code Vehicle} (one source, shared with the earlier
 *       engine-hours endpoint); PTO and hydraulic hours live on the profile. Every reading is also logged,
 *       which is what utilisation is computed from.</li>
 *   <li><b>Service by hours</b> — a profile can carry an hour interval beside the vehicle's own km /
 *       calendar interval; an asset is due if either is. The admins are told when it crosses into due.
 *       Completing a SERVICE work order restarts the hour interval automatically.</li>
 *   <li><b>Trailers</b> — coupled to a hauling vehicle (one host per trailer; a host can haul several).</li>
 *   <li><b>Utilisation</b> — hours worked in a period against what the working weekdays would give at the
 *       asset's target hours per day.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssetService {

    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");
    private static final BigDecimal DEFAULT_TARGET_HOURS = new BigDecimal("8.0");
    private static final int DEFAULT_UTILISATION_DAYS = 30;
    private static final int MAX_UTILISATION_DAYS = 366;
    private static final int MAX_FLEET_UTILISATION_ROWS = 500;

    private final VehicleRepository vehicleRepository;
    private final AssetProfileRepository profileRepository;
    private final AssetMeterReadingRepository meterRepository;
    private final AssetAttachmentRepository attachmentRepository;
    private final VehicleServiceRepository serviceRepository;
    private final FuelFillupRepository fuelFillupRepository;
    private final NotificationService notificationService;
    private final TenantAdminRecipients tenantAdminRecipients;

    // ── Read ─────────────────────────────────────────────────────────────

    /** Every vehicle with its asset class (ROAD_VEHICLE when it has no profile); optionally one class only. */
    @Transactional(readOnly = true)
    public Page<AssetSummaryResponse> list(TenantId tenantId, AssetClass assetClass, Pageable pageable) {
        Page<Object[]> page = assetClass != null
                ? profileRepository.findAssetsByClass(tenantId, assetClass, pageable)
                : profileRepository.findAllAssets(tenantId, pageable);
        return page.map(row -> {
            Vehicle v = (Vehicle) row[0];
            AssetProfile p = (AssetProfile) row[1];
            return toSummary(v, p);
        });
    }

    @Transactional(readOnly = true)
    public EquipmentProfileResponse getProfile(TenantId tenantId, UUID vehicleId) {
        Vehicle v = findVehicle(tenantId, vehicleId);
        return toResponse(v, profileRepository.findByTenantIdAndVehicleId(tenantId, vehicleId).orElse(null), tenantId);
    }

    @Transactional(readOnly = true)
    public Page<MeterReadingResponse> meterHistory(TenantId tenantId, UUID vehicleId, Pageable pageable) {
        findVehicle(tenantId, vehicleId);
        return meterRepository.findByVehicle(tenantId, vehicleId, pageable)
                .map(r -> new MeterReadingResponse(r.getRecordedAt(), r.getEngineHours(), r.getPtoHours(), r.getHydraulicHours()));
    }

    // ── Simple equipment screen (meter + hourly rate) ────────────────────

    /** Every live, non-retired vehicle as a machine: engine hours and hourly operating rate, by registration. */
    @Transactional(readOnly = true)
    public List<EquipmentResponse> listEquipment(TenantId tenantId) {
        List<Vehicle> vehicles = vehicleRepository.findAllActive(tenantId, PageRequest.of(0, 500)).getContent().stream()
                .filter(v -> v.getStatus() != VehicleStatus.RETIRED)
                .sorted(Comparator.comparing(Vehicle::getRegistration, Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();
        if (vehicles.isEmpty()) return List.of();
        Map<UUID, BigDecimal> rates = new HashMap<>();
        for (AssetProfile p : profileRepository.findByTenantIdAndVehicleIdIn(tenantId, vehicles.stream().map(Vehicle::getId).toList())) {
            if (p.getOperatingRatePerHour() != null) rates.put(p.getVehicleId(), p.getOperatingRatePerHour());
        }
        return vehicles.stream().map(v -> toEquipment(v, rates.get(v.getId()))).toList();
    }

    /**
     * Sets the engine-hour meter and/or the hourly operating rate from the simple equipment screen.
     * A null engineHours leaves the meter alone (meters only count up, so a lower reading is refused);
     * a null rate clears the rate. The rate lives on the asset profile, which is created on first use.
     */
    @Transactional
    public EquipmentResponse updateEquipment(TenantId tenantId, UUID vehicleId, UpdateEquipmentRequest req) {
        Vehicle v = findVehicle(tenantId, vehicleId);
        if (req.engineHours() != null && req.engineHours().signum() < 0) {
            throw new IllegalArgumentException("Engine hours cannot be negative");
        }
        if (req.operatingRatePerHour() != null && req.operatingRatePerHour().signum() < 0) {
            throw new IllegalArgumentException("Operating rate per hour cannot be negative");
        }
        if (req.engineHours() != null && req.engineHours().compareTo(v.getEngineHours()) != 0) {
            v.updateEngineHours(req.engineHours());
            vehicleRepository.save(v);
        }
        AssetProfile p = profileRepository.findByTenantIdAndVehicleId(tenantId, vehicleId).orElse(null);
        if (p == null && req.operatingRatePerHour() != null) {
            p = AssetProfile.create(tenantId, vehicleId);
            p.apply(AssetClass.ROAD_VEHICLE, req.operatingRatePerHour(), DEFAULT_TARGET_HOURS, null, null, null, null, null, null);
            profileRepository.save(p);
        } else if (p != null) {
            p.changeOperatingRate(req.operatingRatePerHour());
            profileRepository.save(p);
        }
        return toEquipment(v, p == null ? null : p.getOperatingRatePerHour());
    }

    private EquipmentResponse toEquipment(Vehicle v, BigDecimal rate) {
        return new EquipmentResponse(v.getId(), v.getRegistration(), v.getMake(), v.getModel(), v.getVehicleType(),
                v.getStatus().name(), v.getEngineHours(), rate);
    }

    // ── Configure ────────────────────────────────────────────────────────

    @Transactional
    public EquipmentProfileResponse saveProfile(TenantId tenantId, UUID vehicleId, SaveEquipmentProfileRequest req) {
        Vehicle v = findVehicle(tenantId, vehicleId);
        AssetProfile p = profileRepository.findByTenantIdAndVehicleId(tenantId, vehicleId)
                .orElseGet(() -> AssetProfile.create(tenantId, vehicleId));

        if (req.assetClass() == AssetClass.TRAILER && !profileRepository.findCoupledTo(tenantId, vehicleId).isEmpty()) {
            throw new IllegalArgumentException("This vehicle is hauling trailers — it cannot itself be a trailer");
        }
        p.apply(req.assetClass(), req.operatingRatePerHour(),
                req.targetHoursPerDay() != null ? req.targetHoursPerDay() : DEFAULT_TARGET_HOURS,
                req.serviceIntervalHours(), req.lastServiceHours(), blankToNull(req.siteName()),
                blankToNull(req.axleConfiguration()), blankToNull(req.operatorCompetency()), blankToNull(req.notes()));
        p.seedLastServiceHours(v.getEngineHours());
        profileRepository.save(p);
        log.info("Asset profile saved vehicle={} class={} tenant={}", vehicleId, req.assetClass(), tenantId);
        return toResponse(v, p, tenantId);
    }

    // ── Meters & service ─────────────────────────────────────────────────

    /**
     * Records hour-meter readings. Engine hours go through the vehicle's own floor-guarded update;
     * PTO / hydraulic through the profile. Tells admins when the reading takes the asset into
     * "service due".
     */
    @Transactional
    public EquipmentProfileResponse recordMeters(TenantId tenantId, UUID vehicleId, UUID userId, RecordMetersRequest req) {
        if (req.engineHours() == null && req.ptoHours() == null && req.hydraulicHours() == null) {
            throw new IllegalArgumentException("Give at least one meter reading");
        }
        Vehicle v = findVehicle(tenantId, vehicleId);
        if (v.getLifecycleStatus().isOutOfFleet()) {
            throw new IllegalArgumentException("Vehicle " + v.getRegistration() + " is " + v.getLifecycleStatus()
                    + " and is no longer in the fleet");
        }
        AssetProfile p = profileRepository.findByTenantIdAndVehicleId(tenantId, vehicleId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Configure this vehicle as a fleet asset first (PUT /vehicles/{id}/equipment)"));
        if (p.getAssetClass() == AssetClass.TRAILER) {
            throw new IllegalArgumentException("A trailer has no hour meters");
        }

        Instant at = req.recordedAt() != null ? req.recordedAt() : Instant.now();
        if (at.isAfter(Instant.now().plus(5, ChronoUnit.MINUTES))) {
            throw new IllegalArgumentException("The reading time cannot be in the future");
        }
        List<AssetMeterReading> latest = meterRepository.findByVehicle(tenantId, vehicleId, PageRequest.of(0, 1)).getContent();
        if (!latest.isEmpty() && at.isBefore(latest.get(0).getRecordedAt())) {
            throw new IllegalArgumentException("Readings must be recorded in time order — the latest is from "
                    + latest.get(0).getRecordedAt());
        }

        boolean wasDue = isDue(v, p);
        if (req.engineHours() != null) {
            v.updateEngineHours(req.engineHours());          // floor-guarded: cannot go backwards
        }
        p.updateMeters(req.ptoHours(), req.hydraulicHours(), v.getEngineHours());
        vehicleRepository.save(v);
        profileRepository.save(p);
        meterRepository.save(AssetMeterReading.record(tenantId, vehicleId, at, v.getEngineHours(),
                p.getPtoHours(), p.getHydraulicHours(), userId));

        if (!wasDue && p.isServiceDueByHours(v.getEngineHours())) {
            notifyServiceDue(tenantId, v, p);
        }
        return toResponse(v, p, tenantId);
    }

    /** Restarts the hour-based interval — for a service done outside a work order (inside one, completion does this). */
    @Transactional
    public EquipmentProfileResponse recordServiceDone(TenantId tenantId, UUID vehicleId, RecordServiceDoneRequest req) {
        Vehicle v = findVehicle(tenantId, vehicleId);
        AssetProfile p = profileRepository.findByTenantIdAndVehicleId(tenantId, vehicleId)
                .orElseThrow(() -> new IllegalArgumentException("Configure this vehicle as a fleet asset first"));
        BigDecimal hours = req.hours() != null ? req.hours() : v.getEngineHours();
        if (hours.compareTo(v.getEngineHours()) > 0) {
            throw new IllegalArgumentException("Service hours (" + hours + ") cannot exceed the current engine hours ("
                    + v.getEngineHours() + ")");
        }
        p.recordServiceAt(hours);
        profileRepository.save(p);
        return toResponse(v, p, tenantId);
    }

    // ── Trailers ─────────────────────────────────────────────────────────

    @Transactional
    public EquipmentProfileResponse couple(TenantId tenantId, UUID trailerId, UUID hostId) {
        Vehicle trailer = findVehicle(tenantId, trailerId);
        AssetProfile tp = profileRepository.findByTenantIdAndVehicleId(tenantId, trailerId)
                .orElseThrow(() -> new IllegalArgumentException("Configure this vehicle as a TRAILER first"));
        Vehicle host = findVehicle(tenantId, hostId);
        if (host.getLifecycleStatus().isOutOfFleet() || trailer.getLifecycleStatus().isOutOfFleet()) {
            throw new IllegalArgumentException("Both vehicles must still be in the fleet");
        }
        AssetProfile hp = profileRepository.findByTenantIdAndVehicleId(tenantId, hostId).orElse(null);
        if (hp != null && hp.getAssetClass() == AssetClass.TRAILER) {
            throw new IllegalArgumentException("A trailer cannot haul another trailer");
        }
        tp.couple(hostId);
        profileRepository.save(tp);
        log.info("Trailer {} coupled to {} tenant={}", trailer.getRegistration(), host.getRegistration(), tenantId);
        return toResponse(trailer, tp, tenantId);
    }

    @Transactional
    public EquipmentProfileResponse uncouple(TenantId tenantId, UUID trailerId) {
        Vehicle trailer = findVehicle(tenantId, trailerId);
        AssetProfile tp = profileRepository.findByTenantIdAndVehicleId(tenantId, trailerId)
                .orElseThrow(() -> new IllegalArgumentException("This vehicle is not configured as a trailer"));
        tp.uncouple();
        profileRepository.save(tp);
        return toResponse(trailer, tp, tenantId);
    }

    // ── Utilisation ──────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public UtilisationResponse utilisation(TenantId tenantId, UUID vehicleId, LocalDate from, LocalDate to) {
        Vehicle v = findVehicle(tenantId, vehicleId);
        AssetProfile p = profileRepository.findByTenantIdAndVehicleId(tenantId, vehicleId).orElse(null);
        LocalDate[] period = resolvePeriod(from, to);
        return utilisationFor(tenantId, v, p, period[0], period[1]);
    }

    /** Utilisation for every hours-based asset (or one class), most-used first. Capped at 500 assets. */
    @Transactional(readOnly = true)
    public List<UtilisationResponse> fleetUtilisation(TenantId tenantId, AssetClass assetClass, LocalDate from, LocalDate to) {
        LocalDate[] period = resolvePeriod(from, to);
        var classes = assetClass != null ? EnumSet.of(assetClass) : EnumSet.copyOf(AssetClass.HOURS_BASED);
        return profileRepository.findByClasses(tenantId, classes).stream()
                .limit(MAX_FLEET_UTILISATION_ROWS)
                .map(p -> {
                    Vehicle v = vehicleRepository.findActiveById(tenantId, p.getVehicleId()).orElse(null);
                    return v == null ? null : utilisationFor(tenantId, v, p, period[0], period[1]);
                })
                .filter(u -> u != null)
                .sorted(Comparator.comparing(UtilisationResponse::utilisationPercent,
                        Comparator.nullsLast(Comparator.<Double>naturalOrder().reversed())))
                .toList();
    }

    private UtilisationResponse utilisationFor(TenantId tenantId, Vehicle v, AssetProfile p, LocalDate from, LocalDate to) {
        Instant fromI = from.atStartOfDay(SAST).toInstant();
        Instant toI = to.plusDays(1).atStartOfDay(SAST).toInstant();

        List<AssetMeterReading> inRange = meterRepository.findBetween(tenantId, v.getId(), fromI, toI);
        List<AssetMeterReading> before = meterRepository.findBefore(tenantId, v.getId(), fromI, PageRequest.of(0, 1));
        AssetMeterReading start = !before.isEmpty() ? before.get(0) : (inRange.isEmpty() ? null : inRange.get(0));
        AssetMeterReading end = inRange.isEmpty() ? null : inRange.get(inRange.size() - 1);
        int readingsUsed = inRange.size() + before.size();

        BigDecimal target = p != null ? p.getTargetHoursPerDay() : DEFAULT_TARGET_HOURS;
        int workingDays = workingDays(from, to);
        BigDecimal available = target.multiply(BigDecimal.valueOf(workingDays));
        String assetClass = (p != null ? p.getAssetClass() : AssetClass.ROAD_VEHICLE).name();

        if (start == null || end == null || start.getId().equals(end.getId())) {
            return new UtilisationResponse(v.getId(), v.getRegistration(), assetClass, from, to, readingsUsed,
                    "NO_DATA", null, null, null, workingDays, available, null, null, null);
        }

        BigDecimal engine = end.getEngineHours().subtract(start.getEngineHours());
        BigDecimal pto = delta(end.getPtoHours(), start.getPtoHours());
        BigDecimal hyd = delta(end.getHydraulicHours(), start.getHydraulicHours());
        Double util = available.signum() > 0
                ? Math.round(engine.doubleValue() / available.doubleValue() * 1000.0) / 10.0 : null;
        BigDecimal charge = p != null && p.getOperatingRatePerHour() != null
                ? engine.multiply(p.getOperatingRatePerHour()).setScale(2, RoundingMode.HALF_UP) : null;

        // Approximate: litres put in during the period over the hours worked (assumes a similar tank level at both ends).
        BigDecimal litres = fuelFillupRepository.findByVehicleSinceOrdered(v.getId(), from).stream()
                .filter(f -> !f.getFilledAt().isAfter(to)).map(FuelFillup::getLitres)
                .filter(l -> l != null).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal lph = engine.signum() > 0 && litres.signum() > 0
                ? litres.divide(engine, 1, RoundingMode.HALF_UP) : null;

        return new UtilisationResponse(v.getId(), v.getRegistration(), assetClass, from, to, readingsUsed, "OK",
                engine, pto, hyd, workingDays, available, util, charge, lph);
    }

    private BigDecimal delta(BigDecimal later, BigDecimal earlier) {
        return later != null && earlier != null ? later.subtract(earlier) : null;
    }

    private int workingDays(LocalDate from, LocalDate to) {
        int n = 0;
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            DayOfWeek dow = d.getDayOfWeek();
            if (dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY) n++;
        }
        return n;
    }

    private LocalDate[] resolvePeriod(LocalDate from, LocalDate to) {
        LocalDate end = to != null ? to : LocalDate.now(SAST);
        LocalDate start = from != null ? from : end.minusDays(DEFAULT_UTILISATION_DAYS - 1L);
        if (start.isAfter(end)) throw new IllegalArgumentException("'from' cannot be after 'to'");
        if (ChronoUnit.DAYS.between(start, end) + 1 > MAX_UTILISATION_DAYS) {
            throw new IllegalArgumentException("The period cannot exceed " + MAX_UTILISATION_DAYS + " days");
        }
        return new LocalDate[]{start, end};
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private boolean isDue(Vehicle v, AssetProfile p) {
        return v.isDueForService() || p.isServiceDueByHours(v.getEngineHours());
    }

    private Vehicle findVehicle(TenantId tenantId, UUID id) {
        return vehicleRepository.findActiveById(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle", id.toString()));
    }

    private String blankToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private void notifyServiceDue(TenantId tenantId, Vehicle v, AssetProfile p) {
        List<Recipient> recipients = tenantAdminRecipients.resolveTenantAdmins(tenantId);
        if (recipients.isEmpty()) return;
        notificationService.send(NotificationRequest.builder()
                .tenantId(tenantId)
                .type(NotificationType.VEHICLE_SERVICE_DUE)
                .title("Service due: " + v.getRegistration())
                .message(v.getRegistration() + " has reached " + v.getEngineHours() + " engine hours and is due for service "
                        + "(interval: " + p.getServiceIntervalHours() + " hours).")
                .actionUrl("/fleet/vehicles/" + v.getId())
                .sourceModule("fleet")
                .sourceEntityId(v.getId().toString())
                .recipients(recipients)
                .build());
    }

    // ── Mapping ──────────────────────────────────────────────────────────

    private AssetSummaryResponse toSummary(Vehicle v, AssetProfile p) {
        boolean dueByHours = p != null && p.isServiceDueByHours(v.getEngineHours());
        return new AssetSummaryResponse(v.getId(), v.getRegistration(), v.getMake(), v.getModel(),
                (p != null ? p.getAssetClass() : AssetClass.ROAD_VEHICLE).name(), v.getStatus().name(),
                v.getLifecycleStatus().name(), v.getEngineHours(), v.getCurrentOdometer(),
                v.isDueForService() || dueByHours, p != null ? p.getSiteName() : null,
                v.getAssignedDriverName(), p != null ? p.getCoupledToVehicleId() : null);
    }

    private EquipmentProfileResponse toResponse(Vehicle v, AssetProfile p, TenantId tenantId) {
        BigDecimal engine = v.getEngineHours();
        boolean configured = p != null;

        BigDecimal since = configured ? p.hoursSinceService(engine) : null;
        BigDecimal until = since != null ? BigDecimal.valueOf(p.getServiceIntervalHours()).subtract(since) : null;
        boolean dueByHours = configured && p.isServiceDueByHours(engine);

        BigDecimal totalCost = serviceRepository.sumCostByVehicle(v.getId()).add(fuelFillupRepository.sumCostByVehicle(v.getId()));
        BigDecimal costPerHour = engine.signum() > 0 && totalCost.signum() > 0
                ? totalCost.divide(engine, 2, RoundingMode.HALF_UP) : null;

        String hostReg = null;
        if (configured && p.getCoupledToVehicleId() != null) {
            hostReg = vehicleRepository.findActiveById(tenantId, p.getCoupledToVehicleId())
                    .map(Vehicle::getRegistration).orElse(null);
        }
        Map<UUID, String> regs = new HashMap<>();
        List<EquipmentProfileResponse.LinkedAsset> trailers = profileRepository.findCoupledTo(tenantId, v.getId()).stream()
                .map(t -> new EquipmentProfileResponse.LinkedAsset(t.getVehicleId(), regs.computeIfAbsent(t.getVehicleId(),
                        id -> vehicleRepository.findActiveById(tenantId, id).map(Vehicle::getRegistration).orElse(null))))
                .toList();
        List<EquipmentProfileResponse.LinkedAttachment> attachments = attachmentRepository
                .findAttachedToVehicle(tenantId, v.getId()).stream()
                .map(a -> new EquipmentProfileResponse.LinkedAttachment(a.getId(), a.getName(), a.getCategory(), a.getAttachedAt()))
                .toList();

        return new EquipmentProfileResponse(v.getId(), v.getRegistration(), v.getMake(), v.getModel(), configured,
                (configured ? p.getAssetClass() : AssetClass.ROAD_VEHICLE).name(),
                engine, configured ? p.getPtoHours() : BigDecimal.ZERO, configured ? p.getHydraulicHours() : BigDecimal.ZERO,
                configured ? p.getOperatingRatePerHour() : null, configured ? p.getTargetHoursPerDay() : DEFAULT_TARGET_HOURS,
                costPerHour,
                configured ? p.getServiceIntervalHours() : null, configured ? p.getLastServiceHours() : null,
                since, until, dueByHours, v.isDueForService() || dueByHours,
                configured ? p.getSiteName() : null, configured ? p.getSiteUpdatedAt() : null,
                configured ? p.getAxleConfiguration() : null, configured ? p.getOperatorCompetency() : null,
                v.getAssignedDriverId(), v.getAssignedDriverName(),
                configured ? p.getCoupledToVehicleId() : null, hostReg, trailers, attachments,
                configured ? p.getNotes() : null);
    }
}