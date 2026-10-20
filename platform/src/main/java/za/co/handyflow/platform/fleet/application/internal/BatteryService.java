package za.co.handyflow.platform.fleet.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.fleet.domain.model.Battery;
import za.co.handyflow.platform.fleet.domain.model.BatteryInstallation;
import za.co.handyflow.platform.fleet.domain.model.BatteryStatus;
import za.co.handyflow.platform.fleet.domain.model.BatteryTest;
import za.co.handyflow.platform.fleet.domain.model.BatteryTestResult;
import za.co.handyflow.platform.fleet.domain.model.Vehicle;
import za.co.handyflow.platform.fleet.domain.model.WorkOrder;
import za.co.handyflow.platform.fleet.domain.repository.BatteryInstallationRepository;
import za.co.handyflow.platform.fleet.domain.repository.BatteryRepository;
import za.co.handyflow.platform.fleet.domain.repository.BatteryTestRepository;
import za.co.handyflow.platform.fleet.domain.repository.VehicleRepository;
import za.co.handyflow.platform.fleet.domain.repository.WorkOrderRepository;
import za.co.handyflow.platform.fleet.dto.*;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Battery management: register, install, remove, test, scrap. One record per battery for life;
 * each stay on a vehicle is a {@link BatteryInstallation}. Expiring warranties are announced by
 * {@link FleetBatteryScheduler}; a battery removed as FAULTY while still under warranty is flagged
 * {@code warrantyClaimable} so the claim isn't forgotten.
 * <p>
 * Same odometer rules as tyres: readings default to the vehicle's current odometer, may be lower
 * (back-dating) but never higher. A battery's purchase price lives on the battery — don't also log
 * it as a work-order PART line or the spend is counted twice.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BatteryService {

    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");
    private static final int TEST_HISTORY = 50;
    private static final double DAYS_PER_MONTH = 30.4375;

    private final BatteryRepository batteryRepository;
    private final BatteryInstallationRepository installationRepository;
    private final BatteryTestRepository testRepository;
    private final VehicleRepository vehicleRepository;
    private final WorkOrderRepository workOrderRepository;

    // ── Read ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<BatteryResponse> list(TenantId tenantId, BatteryStatus status, Pageable pageable) {
        Page<Battery> page = status != null
                ? batteryRepository.findByStatus(tenantId, status, pageable)
                : batteryRepository.findAllForTenant(tenantId, pageable);
        Map<UUID, Vehicle> vehicles = new HashMap<>();
        return page.map(b -> toSummary(b, vehicleOf(tenantId, b.getCurrentVehicleId(), vehicles)));
    }

    @Transactional(readOnly = true)
    public BatteryDetailResponse get(TenantId tenantId, UUID id) {
        return toDetail(find(tenantId, id), tenantId);
    }

    @Transactional(readOnly = true)
    public List<BatteryResponse> forVehicle(TenantId tenantId, UUID vehicleId) {
        Vehicle vehicle = findVehicle(tenantId, vehicleId);
        return batteryRepository.findInstalledByVehicle(tenantId, vehicleId).stream()
                .map(b -> toSummary(b, vehicle)).toList();
    }

    // ── Register / edit ──────────────────────────────────────────────────

    @Transactional
    public BatteryDetailResponse create(TenantId tenantId, SaveBatteryRequest req) {
        String serial = req.serialNumber().trim();
        assertSerialFree(tenantId, serial, UUID.randomUUID());
        Battery b = Battery.create(tenantId, serial, req.brand().trim(), blankToNull(req.model()),
                req.nominalVoltage(), req.capacityAh(), blankToNull(req.supplier()), req.purchaseDate(),
                req.purchasePrice(), resolveWarrantyExpiry(req), blankToNull(req.notes()));
        batteryRepository.save(b);
        log.info("Battery registered serial={} tenant={}", serial, tenantId);
        return toDetail(b, tenantId);
    }

    @Transactional
    public BatteryDetailResponse update(TenantId tenantId, UUID id, SaveBatteryRequest req) {
        Battery b = find(tenantId, id);
        String serial = req.serialNumber().trim();
        assertSerialFree(tenantId, serial, id);
        b.updateDetails(serial, req.brand().trim(), blankToNull(req.model()), req.nominalVoltage(),
                req.capacityAh(), blankToNull(req.supplier()), req.purchaseDate(), req.purchasePrice(),
                resolveWarrantyExpiry(req), blankToNull(req.notes()));
        batteryRepository.save(b);
        return toDetail(b, tenantId);
    }

    /** An explicit expiry wins; otherwise purchase date + warranty months; otherwise none. */
    private LocalDate resolveWarrantyExpiry(SaveBatteryRequest req) {
        if (req.warrantyExpiry() != null) return req.warrantyExpiry();
        if (req.warrantyMonths() == null) return null;
        if (req.purchaseDate() == null) {
            throw new IllegalArgumentException("A purchase date is required to work out the warranty from months — "
                    + "or give the warranty expiry date directly");
        }
        return req.purchaseDate().plusMonths(req.warrantyMonths());
    }

    // ── Install / remove ─────────────────────────────────────────────────

    @Transactional
    public BatteryDetailResponse install(TenantId tenantId, UUID id, InstallBatteryRequest req) {
        Battery battery = find(tenantId, id);
        Vehicle vehicle = findVehicle(tenantId, req.vehicleId());
        if (vehicle.getLifecycleStatus().isOutOfFleet()) {
            throw new IllegalArgumentException("Vehicle " + vehicle.getRegistration() + " is "
                    + vehicle.getLifecycleStatus() + " and is no longer in the fleet");
        }
        String position = normalisePosition(req.position() != null ? req.position() : "1");
        batteryRepository.findInstalledAt(tenantId, vehicle.getId(), position).ifPresent(other -> {
            throw new IllegalArgumentException("Position " + position + " on " + vehicle.getRegistration()
                    + " already has battery " + other.getSerialNumber() + " — remove it first");
        });
        int odometer = resolveOdometer(vehicle, req.odometer());
        Instant at = resolveTime(req.installedAt());
        requireWorkOrderOnVehicle(tenantId, req.workOrderId(), vehicle.getId());

        battery.install(vehicle.getId(), position, odometer, at);   // throws unless the battery is IN_STOCK
        batteryRepository.save(battery);
        installationRepository.save(BatteryInstallation.open(tenantId, id, vehicle.getId(), position, at,
                odometer, req.workOrderId()));
        log.info("Battery {} installed on {} position {} tenant={}", battery.getSerialNumber(),
                vehicle.getRegistration(), position, tenantId);
        return toDetail(battery, tenantId);
    }

    @Transactional
    public BatteryDetailResponse remove(TenantId tenantId, UUID id, RemoveBatteryRequest req) {
        Battery battery = find(tenantId, id);
        if (battery.getStatus() != BatteryStatus.INSTALLED) {
            throw new IllegalStateException("Battery " + battery.getSerialNumber() + " is " + battery.getStatus()
                    + ", not installed");
        }
        Vehicle vehicle = vehicleRepository.findActiveById(tenantId, battery.getCurrentVehicleId()).orElse(null);
        int odometer;
        if (vehicle != null) {
            odometer = resolveOdometer(vehicle, req.odometer());
        } else if (req.odometer() != null) {
            odometer = req.odometer();
        } else {
            throw new IllegalArgumentException("An odometer reading is required (the vehicle is no longer on file)");
        }
        Instant at = resolveTime(req.removedAt());
        requireWorkOrderOnVehicle(tenantId, req.workOrderId(), battery.getCurrentVehicleId());

        BatteryInstallation open = installationRepository.findOpenByBattery(tenantId, id)
                .orElseThrow(() -> new IllegalStateException("No open installation found for battery " + battery.getSerialNumber()));
        open.close(at, odometer, req.reason(), req.workOrderId());
        battery.remove(odometer, req.outcome(), at, blankToNull(req.scrapReason()));
        installationRepository.save(open);
        batteryRepository.save(battery);
        log.info("Battery {} removed outcome={} reason={} tenant={}", battery.getSerialNumber(),
                req.outcome(), req.reason(), tenantId);
        return toDetail(battery, tenantId);
    }

    // ── Testing & scrap ──────────────────────────────────────────────────

    @Transactional
    public BatteryDetailResponse recordTest(TenantId tenantId, UUID id, UUID userId, RecordBatteryTestRequest req) {
        Battery battery = find(tenantId, id);
        Instant at = resolveTime(req.testedAt());
        BatteryTestResult result = battery.recordTest(req.voltage(), at);
        batteryRepository.save(battery);
        testRepository.save(BatteryTest.record(tenantId, id, battery.getCurrentVehicleId(), at, req.voltage(),
                result, blankToNull(req.notes()), userId));
        return toDetail(battery, tenantId);
    }

    @Transactional
    public BatteryDetailResponse scrap(TenantId tenantId, UUID id, ScrapAssetRequest req) {
        Battery battery = find(tenantId, id);
        battery.scrap(blankToNull(req.reason()), resolveTime(req.scrappedAt()));
        batteryRepository.save(battery);
        return toDetail(battery, tenantId);
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private Battery find(TenantId tenantId, UUID id) {
        return batteryRepository.findByTenantAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Battery", id.toString()));
    }

    private Vehicle findVehicle(TenantId tenantId, UUID id) {
        return vehicleRepository.findActiveById(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle", id.toString()));
    }

    private Vehicle vehicleOf(TenantId tenantId, UUID vehicleId, Map<UUID, Vehicle> cache) {
        if (vehicleId == null) return null;
        return cache.computeIfAbsent(vehicleId, vid -> vehicleRepository.findActiveById(tenantId, vid).orElse(null));
    }

    private void assertSerialFree(TenantId tenantId, String serial, UUID excludeId) {
        if (batteryRepository.existsOtherWithSerial(tenantId, serial, excludeId)) {
            throw new IllegalArgumentException("A battery with serial number " + serial + " already exists");
        }
    }

    private String normalisePosition(String raw) {
        String p = raw.trim().toUpperCase().replace(' ', '_');
        if (!p.matches("[A-Z0-9_-]{1,10}")) {
            throw new IllegalArgumentException("Position must be 1–10 letters, digits, '-' or '_' (for example 1 or 2)");
        }
        return p;
    }

    private int resolveOdometer(Vehicle vehicle, Integer requested) {
        int current = vehicle.getCurrentOdometer() != null ? vehicle.getCurrentOdometer() : 0;
        if (requested == null) return current;
        if (requested > current) {
            throw new IllegalArgumentException("Odometer (" + requested + ") is above the vehicle's current reading ("
                    + current + ") — record the vehicle's odometer first");
        }
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

    private String blankToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    // ── Mapping ──────────────────────────────────────────────────────────

    private BatteryResponse toSummary(Battery b, Vehicle vehicle) {
        LocalDate today = LocalDate.now(SAST);
        return new BatteryResponse(b.getId(), b.getSerialNumber(), b.getBrand(), b.getModel(), b.getNominalVoltage(),
                b.getCapacityAh(), b.getStatus().name(), b.getCurrentVehicleId(),
                vehicle != null ? vehicle.getRegistration() : null, b.getCurrentPosition(), b.getWarrantyExpiry(),
                b.isInWarranty(today), b.getLastVoltage(),
                b.getLastTestResult() != null ? b.getLastTestResult().name() : null, b.getLastTestedAt(),
                b.totalDaysInService(Instant.now()));
    }

    private BatteryDetailResponse toDetail(Battery b, TenantId tenantId) {
        Map<UUID, Vehicle> vehicles = new HashMap<>();
        Vehicle current = vehicleOf(tenantId, b.getCurrentVehicleId(), vehicles);
        LocalDate today = LocalDate.now(SAST);

        List<BatteryInstallationResponse> installations = installationRepository.findByBattery(tenantId, b.getId())
                .stream().map(i -> {
                    Vehicle v = vehicleOf(tenantId, i.getVehicleId(), vehicles);
                    return new BatteryInstallationResponse(i.getId(), i.getVehicleId(),
                            v != null ? v.getRegistration() : null, i.getPosition(), i.getInstalledAt(),
                            i.getInstalledOdometer(), i.getRemovedAt(), i.getRemovedOdometer(), i.getKm(),
                            i.getRemovalReason() != null ? i.getRemovalReason().name() : null,
                            i.getInstallWorkOrderId(), i.getRemoveWorkOrderId());
                }).toList();
        List<BatteryTestResponse> tests = testRepository
                .findByBattery(tenantId, b.getId(), PageRequest.of(0, TEST_HISTORY)).stream()
                .map(t -> new BatteryTestResponse(t.getTestedAt(), t.getVoltage(), t.getResult().name(), t.getNotes()))
                .toList();

        Long daysToWarranty = b.getWarrantyExpiry() != null
                ? ChronoUnit.DAYS.between(today, b.getWarrantyExpiry()) : null;
        int days = b.totalDaysInService(Instant.now());
        BigDecimal costPerMonth = days > 0 && b.getPurchasePrice() != null && b.getPurchasePrice().signum() > 0
                ? b.getPurchasePrice().divide(BigDecimal.valueOf(days / DAYS_PER_MONTH), 2, RoundingMode.HALF_UP)
                : null;

        return new BatteryDetailResponse(toSummary(b, current), b.getSupplier(), b.getPurchaseDate(),
                b.getPurchasePrice(), daysToWarranty,
                b.getStatus() == BatteryStatus.FAULTY && b.isInWarranty(today), costPerMonth, b.getNotes(),
                b.getScrappedAt(), b.getScrapReason(), installations, tests);
    }
}