package za.co.handyflow.platform.fleet.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.fleet.domain.model.TreadStatus;
import za.co.handyflow.platform.fleet.domain.model.Tyre;
import za.co.handyflow.platform.fleet.domain.model.TyreFitment;
import za.co.handyflow.platform.fleet.domain.model.TyreMeasurement;
import za.co.handyflow.platform.fleet.domain.model.TyreRemovalReason;
import za.co.handyflow.platform.fleet.domain.model.TyreRetread;
import za.co.handyflow.platform.fleet.domain.model.TyreStatus;
import za.co.handyflow.platform.fleet.domain.model.Vehicle;
import za.co.handyflow.platform.fleet.domain.model.WorkOrder;
import za.co.handyflow.platform.fleet.domain.repository.TyreFitmentRepository;
import za.co.handyflow.platform.fleet.domain.repository.TyreMeasurementRepository;
import za.co.handyflow.platform.fleet.domain.repository.TyreRepository;
import za.co.handyflow.platform.fleet.domain.repository.TyreRetreadRepository;
import za.co.handyflow.platform.fleet.domain.repository.VehicleRepository;
import za.co.handyflow.platform.fleet.domain.repository.WorkOrderRepository;
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
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * *** ACTION NEEDED IN YOUR NotificationType.java *** (Fleet section):
 * <pre>{@code
 * TYRE_TREAD_LOW(WARNING, Set.of(IN_APP, EMAIL)),
 * }</pre>
 * This won't compile until the constant is added.
 * <p>
 * Tyre management: register, fit, remove, rotate, measure tread, retread, scrap. A tyre is one
 * record for life; every stay on a vehicle is a {@link TyreFitment}, so lifetime kilometres and
 * cost-per-km follow the tyre wherever it goes.
 * <p>
 * Notes:
 * <ul>
 *   <li>Odometer readings default to the vehicle's current odometer and may not exceed it; a lower
 *       one back-dates the event. Tyre kilometres are derived from vehicle odometers, so they are only
 *       as good as those readings.</li>
 *   <li>Rotation is two-phase (park, then place) so tyres can swap positions without tripping the
 *       one-tyre-per-position index.</li>
 *   <li>A tyre's purchase price lives on the tyre. If a fitting work order also logs the tyre as a PART
 *       line, that spend is counted twice in fleet cost reports — log one or the other.</li>
 *   <li>Tyres fitted to a vehicle are not removed automatically when the vehicle is sold or written
 *       off; remove them (reason VEHICLE_DISPOSED) to bring them back into stock.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TyreService {

    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");
    private static final int MEASUREMENT_HISTORY = 50;
    private static final String TEMP_POSITION_PREFIX = "~";

    private final TyreRepository tyreRepository;
    private final TyreFitmentRepository fitmentRepository;
    private final TyreMeasurementRepository measurementRepository;
    private final TyreRetreadRepository retreadRepository;
    private final VehicleRepository vehicleRepository;
    private final WorkOrderRepository workOrderRepository;
    private final NotificationService notificationService;
    private final TenantAdminRecipients tenantAdminRecipients;

    // ── Read ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<TyreResponse> list(TenantId tenantId, TyreStatus status, Pageable pageable) {
        Page<Tyre> page = status != null
                ? tyreRepository.findByStatus(tenantId, status, pageable)
                : tyreRepository.findAllForTenant(tenantId, pageable);
        Map<UUID, Vehicle> vehicles = new HashMap<>();
        return page.map(t -> toSummary(t, vehicleOf(tenantId, t.getCurrentVehicleId(), vehicles)));
    }

    @Transactional(readOnly = true)
    public TyreDetailResponse get(TenantId tenantId, UUID id) {
        return toDetail(find(tenantId, id), tenantId);
    }

    /** The tyres currently on a vehicle, in position order. */
    @Transactional(readOnly = true)
    public List<TyreResponse> forVehicle(TenantId tenantId, UUID vehicleId) {
        Vehicle vehicle = findVehicle(tenantId, vehicleId);
        return tyreRepository.findFittedByVehicle(tenantId, vehicleId).stream()
                .map(t -> toSummary(t, vehicle)).toList();
    }

    // ── Register / edit ──────────────────────────────────────────────────

    @Transactional
    public TyreDetailResponse create(TenantId tenantId, SaveTyreRequest req) {
        String serial = req.serialNumber().trim();
        assertSerialFree(tenantId, serial, UUID.randomUUID());
        Tyre tyre = Tyre.create(tenantId, serial, req.brand().trim(), blankToNull(req.pattern()), req.size().trim(),
                req.purchaseDate(), req.purchasePrice(), blankToNull(req.supplier()),
                req.initialTreadDepthMm(), blankToNull(req.notes()));
        tyreRepository.save(tyre);
        log.info("Tyre registered serial={} tenant={}", serial, tenantId);
        return toDetail(tyre, tenantId);
    }

    @Transactional
    public TyreDetailResponse update(TenantId tenantId, UUID id, SaveTyreRequest req) {
        Tyre tyre = find(tenantId, id);
        String serial = req.serialNumber().trim();
        assertSerialFree(tenantId, serial, id);
        tyre.updateDetails(serial, req.brand().trim(), blankToNull(req.pattern()), req.size().trim(),
                req.purchaseDate(), req.purchasePrice(), blankToNull(req.supplier()),
                req.initialTreadDepthMm(), blankToNull(req.notes()));
        tyreRepository.save(tyre);
        return toDetail(tyre, tenantId);
    }

    // ── Fit / remove / rotate ────────────────────────────────────────────

    @Transactional
    public TyreDetailResponse fit(TenantId tenantId, UUID id, FitTyreRequest req) {
        Tyre tyre = find(tenantId, id);
        Vehicle vehicle = findVehicle(tenantId, req.vehicleId());
        if (vehicle.getLifecycleStatus().isOutOfFleet()) {
            throw new IllegalArgumentException("Vehicle " + vehicle.getRegistration() + " is "
                    + vehicle.getLifecycleStatus() + " and is no longer in the fleet");
        }
        String position = normalisePosition(req.position());
        if (isWheelPosition(position)) {
            tyreRepository.findFittedAt(tenantId, vehicle.getId(), position).ifPresent(other -> {
                throw new IllegalArgumentException("Position " + position + " on " + vehicle.getRegistration()
                        + " already has tyre " + other.getSerialNumber() + " — remove or rotate it first");
            });
        }
        int odometer = resolveOdometer(vehicle, req.odometer());
        Instant at = resolveTime(req.fittedAt());
        requireWorkOrderOnVehicle(tenantId, req.workOrderId(), vehicle.getId());

        tyre.fit(vehicle.getId(), position, odometer, at);   // throws unless the tyre is IN_STOCK
        tyreRepository.save(tyre);
        fitmentRepository.save(TyreFitment.open(tenantId, id, vehicle.getId(), position, at, odometer, req.workOrderId()));
        log.info("Tyre {} fitted to {} at {} tenant={}", tyre.getSerialNumber(), vehicle.getRegistration(), position, tenantId);
        return toDetail(tyre, tenantId);
    }

    @Transactional
    public TyreDetailResponse remove(TenantId tenantId, UUID id, RemoveTyreRequest req) {
        Tyre tyre = find(tenantId, id);
        if (tyre.getStatus() != TyreStatus.FITTED) {
            throw new IllegalStateException("Tyre " + tyre.getSerialNumber() + " is " + tyre.getStatus() + ", not fitted");
        }
        Vehicle vehicle = vehicleRepository.findActiveById(tenantId, tyre.getCurrentVehicleId()).orElse(null);
        int odometer = resolveRemovalOdometer(vehicle, req.odometer());
        Instant at = resolveTime(req.removedAt());
        requireWorkOrderOnVehicle(tenantId, req.workOrderId(), tyre.getCurrentVehicleId());

        TyreFitment open = fitmentRepository.findOpenByTyre(tenantId, id)
                .orElseThrow(() -> new IllegalStateException("No open fitment found for tyre " + tyre.getSerialNumber()));
        open.close(at, odometer, req.reason(), req.workOrderId());
        tyre.remove(odometer, req.outcome(), at, blankToNull(req.scrapReason()));
        fitmentRepository.save(open);
        tyreRepository.save(tyre);
        log.info("Tyre {} removed outcome={} reason={} tenant={}", tyre.getSerialNumber(), req.outcome(), req.reason(), tenantId);
        return toDetail(tyre, tenantId);
    }

    /**
     * Rotates tyres on one vehicle in a single transaction, swaps included. Phase 1 closes each moving
     * tyre's fitment and parks it on a temporary position; phase 2 (after a flush) places it on its
     * final position and opens the new fitment. The final layout is validated first.
     */
    @Transactional
    public List<TyreResponse> rotate(TenantId tenantId, UUID vehicleId, RotateTyresRequest req) {
        Vehicle vehicle = findVehicle(tenantId, vehicleId);
        int odometer = resolveOdometer(vehicle, req.odometer());
        Instant at = resolveTime(req.rotatedAt());
        requireWorkOrderOnVehicle(tenantId, req.workOrderId(), vehicleId);

        List<Tyre> fitted = tyreRepository.findFittedByVehicle(tenantId, vehicleId);
        Map<UUID, Tyre> byId = fitted.stream().collect(Collectors.toMap(Tyre::getId, Function.identity()));

        Map<UUID, String> target = new LinkedHashMap<>();
        for (RotateTyresRequest.Move m : req.moves()) {
            if (!byId.containsKey(m.tyreId())) {
                throw new IllegalArgumentException("Tyre " + m.tyreId() + " is not fitted to " + vehicle.getRegistration());
            }
            if (target.put(m.tyreId(), normalisePosition(m.toPosition())) != null) {
                throw new IllegalArgumentException("Tyre " + m.tyreId() + " appears more than once in the rotation");
            }
        }

        // Validate the layout the vehicle would end up with.
        Set<String> seen = new HashSet<>();
        for (Tyre t : fitted) {
            String finalPos = target.getOrDefault(t.getId(), t.getCurrentPosition());
            if (isWheelPosition(finalPos) && !seen.add(finalPos)) {
                throw new IllegalArgumentException("Position " + finalPos + " would hold more than one tyre");
            }
        }
        List<Tyre> moving = target.entrySet().stream()
                .filter(e -> !e.getValue().equals(byId.get(e.getKey()).getCurrentPosition()))
                .map(e -> byId.get(e.getKey())).toList();
        if (moving.isEmpty()) {
            throw new IllegalArgumentException("No tyre would change position");
        }

        // Phase 1: close fitments and park on temporary positions.
        int n = 0;
        for (Tyre t : moving) {
            TyreFitment open = fitmentRepository.findOpenByTyre(tenantId, t.getId())
                    .orElseThrow(() -> new IllegalStateException("No open fitment found for tyre " + t.getSerialNumber()));
            open.close(at, odometer, TyreRemovalReason.ROTATION, req.workOrderId());
            fitmentRepository.save(open);
            t.parkForRotation(TEMP_POSITION_PREFIX + (++n));
            tyreRepository.save(t);
        }
        fitmentRepository.flush();
        tyreRepository.flush();

        // Phase 2: final positions and the new fitments.
        for (Tyre t : moving) {
            String newPos = target.get(t.getId());
            t.rotate(newPos, odometer, at);
            tyreRepository.save(t);
            fitmentRepository.save(TyreFitment.open(tenantId, t.getId(), vehicleId, newPos, at, odometer, req.workOrderId()));
        }
        log.info("Rotated {} tyre(s) on {} tenant={}", moving.size(), vehicle.getRegistration(), tenantId);
        return tyreRepository.findFittedByVehicle(tenantId, vehicleId).stream().map(t -> toSummary(t, vehicle)).toList();
    }

    // ── Wear, retread, scrap ─────────────────────────────────────────────

    @Transactional
    public TyreDetailResponse recordMeasurement(TenantId tenantId, UUID id, UUID userId,
                                                RecordTreadMeasurementRequest req) {
        Tyre tyre = find(tenantId, id);
        Instant at = resolveTime(req.measuredAt());
        TreadStatus before = Tyre.classify(tyre.getLastTreadDepthMm());

        tyre.recordMeasurement(req.treadDepthMm(), at);   // rejects an impossible increase
        tyreRepository.save(tyre);
        measurementRepository.save(TyreMeasurement.record(tenantId, id, tyre.getCurrentVehicleId(), at,
                req.treadDepthMm(), req.pressureKpa(), req.odometer(), blankToNull(req.notes()), userId));

        TreadStatus after = tyre.treadStatus();
        if (severity(after) > severity(before) && severity(after) > 0) {
            notifyTreadLow(tenantId, tyre, after);
        }
        return toDetail(tyre, tenantId);
    }

    @Transactional
    public TyreDetailResponse completeRetread(TenantId tenantId, UUID id, UUID userId, CompleteRetreadRequest req) {
        Tyre tyre = find(tenantId, id);
        tyre.completeRetread(req.newTreadDepthMm());   // throws unless AWAITING_RETREAD
        tyreRepository.save(tyre);
        retreadRepository.save(TyreRetread.record(tenantId, id,
                req.retreadedAt() != null ? req.retreadedAt() : LocalDate.now(SAST), blankToNull(req.supplier()),
                req.cost(), req.newTreadDepthMm(), blankToNull(req.notes()), userId));
        return toDetail(tyre, tenantId);
    }

    @Transactional
    public TyreDetailResponse scrap(TenantId tenantId, UUID id, ScrapAssetRequest req) {
        Tyre tyre = find(tenantId, id);
        tyre.scrap(blankToNull(req.reason()), resolveTime(req.scrappedAt()));
        tyreRepository.save(tyre);
        return toDetail(tyre, tenantId);
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private Tyre find(TenantId tenantId, UUID id) {
        return tyreRepository.findByTenantAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Tyre", id.toString()));
    }

    private Vehicle findVehicle(TenantId tenantId, UUID id) {
        return vehicleRepository.findActiveById(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle", id.toString()));
    }

    private Vehicle vehicleOf(TenantId tenantId, UUID vehicleId, Map<UUID, Vehicle> cache) {
        if (vehicleId == null) return null;
        return cache.computeIfAbsent(vehicleId, id -> vehicleRepository.findActiveById(tenantId, id).orElse(null));
    }

    private void assertSerialFree(TenantId tenantId, String serial, UUID excludeId) {
        if (tyreRepository.existsOtherWithSerial(tenantId, serial, excludeId)) {
            throw new IllegalArgumentException("A tyre with serial number " + serial + " already exists");
        }
    }

    /** Wheel positions are unique per vehicle; spare positions (SPARE, SPARE2, ...) are not. */
    private boolean isWheelPosition(String position) {
        return !position.startsWith("SPARE");
    }

    private String normalisePosition(String raw) {
        if (raw == null || raw.isBlank()) throw new IllegalArgumentException("A position is required");
        String p = raw.trim().toUpperCase().replace(' ', '_');
        if (!p.matches("[A-Z0-9_-]{1,10}")) {
            throw new IllegalArgumentException(
                    "Position must be 1–10 letters, digits, '-' or '_' (for example FL, FR, RL, RR, A2LO, SPARE)");
        }
        return p;
    }

    /** Defaults to the vehicle's current odometer; may be lower (back-dating) but never higher. */
    private int resolveOdometer(Vehicle vehicle, Integer requested) {
        int current = vehicle.getCurrentOdometer() != null ? vehicle.getCurrentOdometer() : 0;
        if (requested == null) return current;
        if (requested > current) {
            throw new IllegalArgumentException("Odometer (" + requested + ") is above the vehicle's current reading ("
                    + current + ") — record the vehicle's odometer first");
        }
        return requested;
    }

    /** As {@link #resolveOdometer}, but the vehicle may no longer exist, in which case a reading must be supplied. */
    private int resolveRemovalOdometer(Vehicle vehicle, Integer requested) {
        if (vehicle != null) return resolveOdometer(vehicle, requested);
        if (requested == null) throw new IllegalArgumentException("An odometer reading is required (the vehicle is no longer on file)");
        return requested;
    }

    private Instant resolveTime(Instant requested) {
        Instant now = Instant.now();
        if (requested == null) return now;
        if (requested.isAfter(now.plus(5, ChronoUnit.MINUTES))) {
            throw new IllegalArgumentException("The date/time cannot be in the future");
        }
        return requested;
    }

    private void requireWorkOrderOnVehicle(TenantId tenantId, UUID workOrderId, UUID vehicleId) {
        if (workOrderId == null) return;
        WorkOrder wo = workOrderRepository.findByTenantAndId(tenantId, workOrderId)
                .orElseThrow(() -> new ResourceNotFoundException("WorkOrder", workOrderId.toString()));
        if (!wo.getVehicleId().equals(vehicleId)) {
            throw new IllegalArgumentException("That work order is for a different vehicle");
        }
    }

    private int severity(TreadStatus s) {
        return switch (s) {
            case REPLACE_SOON -> 1;
            case REPLACE_NOW -> 2;
            default -> 0;
        };
    }

    private void notifyTreadLow(TenantId tenantId, Tyre tyre, TreadStatus status) {
        List<Recipient> recipients = tenantAdminRecipients.resolveTenantAdmins(tenantId);
        if (recipients.isEmpty()) return;
        String where = "";
        if (tyre.getCurrentVehicleId() != null) {
            String reg = vehicleRepository.findActiveById(tenantId, tyre.getCurrentVehicleId())
                    .map(Vehicle::getRegistration).orElse(null);
            where = " on " + (reg != null ? reg : "a vehicle") + " (" + tyre.getCurrentPosition() + ")";
        }
        boolean now = status == TreadStatus.REPLACE_NOW;
        notificationService.send(NotificationRequest.builder()
                .tenantId(tenantId)
                .type(NotificationType.TYRE_TREAD_LOW)
                .title((now ? "Tyre at legal minimum: " : "Tyre tread low: ") + tyre.getSerialNumber())
                .message("Tyre " + tyre.getSerialNumber() + where + " measured " + tyre.getLastTreadDepthMm()
                        + " mm — " + (now ? "at or below the legal minimum; replace it now."
                        : "below " + Tyre.REPLACE_SOON_MM + " mm; plan a replacement or retread."))
                .actionUrl("/fleet/tyres/" + tyre.getId())
                .sourceModule("fleet")
                .sourceEntityId(tyre.getId().toString())
                .recipients(recipients)
                .build());
    }

    private String blankToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    // ── Mapping ──────────────────────────────────────────────────────────

    private TyreResponse toSummary(Tyre t, Vehicle vehicle) {
        Integer odometer = vehicle != null ? vehicle.getCurrentOdometer() : null;
        return new TyreResponse(t.getId(), t.getSerialNumber(), t.getBrand(), t.getPattern(), t.getSize(),
                t.getStatus().name(), t.getCurrentVehicleId(), vehicle != null ? vehicle.getRegistration() : null,
                t.getCurrentPosition(), t.getLastTreadDepthMm(), t.treadStatus().name(), t.getLastMeasuredAt(),
                t.kmInService(odometer), t.getRetreadCount());
    }

    private TyreDetailResponse toDetail(Tyre t, TenantId tenantId) {
        Map<UUID, Vehicle> vehicles = new HashMap<>();
        Vehicle current = vehicleOf(tenantId, t.getCurrentVehicleId(), vehicles);

        List<TyreFitmentResponse> fitments = fitmentRepository.findByTyre(tenantId, t.getId()).stream()
                .map(f -> {
                    Vehicle v = vehicleOf(tenantId, f.getVehicleId(), vehicles);
                    return new TyreFitmentResponse(f.getId(), f.getVehicleId(), v != null ? v.getRegistration() : null,
                            f.getPosition(), f.getFittedAt(), f.getFittedOdometer(), f.getRemovedAt(),
                            f.getRemovedOdometer(), f.getKm(),
                            f.getRemovalReason() != null ? f.getRemovalReason().name() : null,
                            f.getFitWorkOrderId(), f.getRemoveWorkOrderId());
                }).toList();
        List<TyreMeasurementResponse> measurements = measurementRepository
                .findByTyre(tenantId, t.getId(), PageRequest.of(0, MEASUREMENT_HISTORY)).stream()
                .map(m -> new TyreMeasurementResponse(m.getMeasuredAt(), m.getTreadDepthMm(),
                        Tyre.classify(m.getTreadDepthMm()).name(), m.getPressureKpa(), m.getOdometer(), m.getNotes()))
                .toList();
        List<TyreRetread> retreads = retreadRepository.findByTyre(tenantId, t.getId());

        BigDecimal retreadCost = retreads.stream().map(TyreRetread::getCost).filter(c -> c != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCost = (t.getPurchasePrice() != null ? t.getPurchasePrice() : BigDecimal.ZERO).add(retreadCost);
        int km = t.kmInService(current != null ? current.getCurrentOdometer() : null);
        BigDecimal costPerKm = km > 0 && totalCost.signum() > 0
                ? totalCost.divide(BigDecimal.valueOf(km), 3, RoundingMode.HALF_UP) : null;

        return new TyreDetailResponse(toSummary(t, current), t.getPurchaseDate(), t.getPurchasePrice(),
                t.getSupplier(), t.getInitialTreadDepthMm(), t.getNotes(), t.getScrappedAt(), t.getScrapReason(),
                totalCost, costPerKm, fitments, measurements,
                retreads.stream().map(r -> new TyreRetreadResponse(r.getRetreadedAt(), r.getSupplier(), r.getCost(),
                        r.getNewTreadDepthMm(), r.getNotes())).toList());
    }
}