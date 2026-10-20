package za.co.handyflow.platform.fleet.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.fleet.domain.model.DrivabilityRules;
import za.co.handyflow.platform.fleet.domain.model.Driver;
import za.co.handyflow.platform.fleet.domain.model.FuelFillup;
import za.co.handyflow.platform.fleet.domain.model.Trip;
import za.co.handyflow.platform.fleet.domain.model.Vehicle;
import za.co.handyflow.platform.fleet.domain.model.LifecycleEventType;
import za.co.handyflow.platform.fleet.domain.model.VehicleLifecycleEvent;
import za.co.handyflow.platform.fleet.domain.model.VehicleLifecycleStatus;
import za.co.handyflow.platform.fleet.domain.model.VehicleService;
import za.co.handyflow.platform.fleet.domain.model.VehicleStatus;
import za.co.handyflow.platform.fleet.domain.repository.*;
import za.co.handyflow.platform.fleet.dto.*;
import za.co.handyflow.platform.notifications.application.NotificationRequest;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.TenantAdminRecipients;
import za.co.handyflow.platform.notifications.application.internal.NotificationService;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.shared.UserContext;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FleetService {

    private final VehicleRepository        vehicleRepository;
    private final VehicleServiceRepository serviceRepository;
    private final TripRepository           tripRepository;
    private final FuelFillupRepository     fuelFillupRepository;
    private final VehicleLifecycleEventRepository lifecycleEventRepository;
    private final DriverRepository         driverRepository;
    private final NotificationService      notificationService;
    // Same shared port earthmoving uses — see TenantAdminRecipientsImpl in
    // the identity module. No fleet-specific recipients port; that lesson
    // was already learned building earthmoving's equivalent.
    private final TenantAdminRecipients    tenantAdminRecipients;
    private final AccidentRepository       accidentRepository;

    // ── Vehicles ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<VehicleResponse> getVehicles(TenantId tenantId, String status,
                                             String vehicleType, Pageable pageable) {
        boolean hasStatus = status != null && !status.isBlank();
        boolean hasType   = vehicleType != null && !vehicleType.isBlank();

        if (!hasStatus && !hasType)
            return vehicleRepository.findAllActive(tenantId, pageable).map(this::toResponse);
        if (hasStatus && !hasType)
            return vehicleRepository.findByStatus(tenantId, parseStatus(status), pageable).map(this::toResponse);
        if (!hasStatus)
            return vehicleRepository.findByType(tenantId, vehicleType.toUpperCase(), pageable).map(this::toResponse);
        return vehicleRepository.findByStatusAndType(tenantId, parseStatus(status),
                vehicleType.toUpperCase(), pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public VehicleResponse getVehicle(TenantId tenantId, UUID id) {
        return vehicleRepository.findActiveById(tenantId, id)
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle", id.toString()));
    }

    @Transactional
    public VehicleResponse createVehicle(TenantId tenantId, CreateVehicleRequest req) {
        String registration = req.registration().toUpperCase().trim();
        String vin          = normaliseIdentifier(req.vin());
        String engineNumber = normaliseIdentifier(req.engineNumber());

        // Fresh random id: on create there is no "self" to exclude.
        assertIdentifiersUnique(tenantId, UUID.randomUUID(), registration, vin, engineNumber);

        Vehicle vehicle = Vehicle.create(
                tenantId,
                registration,
                req.make(), req.model(), req.year(),
                req.colour(), vin,
                req.vehicleType(), req.fuelType(),
                req.licenceDiscExpiry(), req.roadworthyExpiry(), req.insuranceExpiry(),
                req.dailyRate(), req.tankCapacityLitres(),
                req.serviceIntervalKm() != null ? req.serviceIntervalKm() : 10000,
                req.serviceIntervalDays(),
                req.assignedDriverName(),
                req.notes()
        );
        vehicle.updateIdentity(
                vin, engineNumber, blankToNull(req.variant()), blankToNull(req.bodyType()),
                req.transmission(), req.driveType(), req.axleCount(),
                req.gvmKg(), req.tareKg(), req.payloadKg(), req.gcmKg(),
                req.acquisitionDate(), req.acquisitionMethod(), req.purchasePrice(),
                req.currentBookValue(), req.currentEstimatedValue(), req.valuedAt()
        );

        vehicleRepository.save(vehicle);
        log.info("Registered vehicle={} tenant={}", vehicle.getRegistration(), tenantId);
        return toResponse(vehicle);
    }

    /**
     * NEW: edit a vehicle's details and identity. Previously there was no edit
     * endpoint at all — a typo in the registration or VIN at creation could
     * never be corrected.
     */
    @Transactional
    public VehicleResponse updateVehicle(TenantId tenantId, UUID id, UpdateVehicleRequest req) {
        Vehicle vehicle = findActive(tenantId, id);

        String registration = req.registration().toUpperCase().trim();
        String vin          = normaliseIdentifier(req.vin());
        String engineNumber = normaliseIdentifier(req.engineNumber());
        assertIdentifiersUnique(tenantId, id, registration, vin, engineNumber);

        vehicle.updateDetails(
                registration, req.make(), req.model(), req.year(),
                req.colour(), req.vehicleType(), req.fuelType(),
                req.licenceDiscExpiry(), req.roadworthyExpiry(), req.insuranceExpiry(),
                req.dailyRate(), req.tankCapacityLitres(),
                req.serviceIntervalKm(), req.serviceIntervalDays(), req.notes()
        );
        vehicle.updateIdentity(
                vin, engineNumber, blankToNull(req.variant()), blankToNull(req.bodyType()),
                req.transmission(), req.driveType(), req.axleCount(),
                req.gvmKg(), req.tareKg(), req.payloadKg(), req.gcmKg(),
                req.acquisitionDate(), req.acquisitionMethod(), req.purchasePrice(),
                req.currentBookValue(), req.currentEstimatedValue(), req.valuedAt()
        );
        vehicleRepository.save(vehicle);
        log.info("Vehicle updated vehicle={} tenant={}", id, tenantId);
        return toResponse(vehicle);
    }

    @Transactional
    public VehicleResponse updateEngineHours(TenantId tenantId, UUID id, UpdateEngineHoursRequest req) {
        Vehicle vehicle = findActive(tenantId, id);
        vehicle.updateEngineHours(req.engineHours());
        vehicleRepository.save(vehicle);
        log.info("Engine hours updated vehicle={} hours={}", id, req.engineHours());
        return toResponse(vehicle);
    }

    @Transactional
    public VehicleResponse updateStatus(TenantId tenantId, UUID id, UpdateVehicleStatusRequest req) {
        Vehicle vehicle = findActive(tenantId, id);
        VehicleStatus target = parseStatus(req.status());

        // NOTE: throws InvalidVehicleStatusTransitionException (a subclass of
        // IllegalStateException) if the transition isn't legal from the
        // vehicle's current state — see VehicleStatus for the full table.
        // The global exception handler maps that to HTTP 409 Conflict.
        // Going on a trip, or back into service from the workshop, is refused while an accident case says the vehicle cannot be driven.
        // (Ending a trip is not blocked: a vehicle that crashed mid-trip must be able to come off it.)
        if (target == VehicleStatus.ON_TRIP || (target == VehicleStatus.AVAILABLE && vehicle.getStatus() != VehicleStatus.ON_TRIP)) {
            String undrivable = DrivabilityRules.problem(accidentRepository.findUndrivableOpen(tenantId, id));
            if (undrivable != null) throw new IllegalStateException(undrivable);
        }
        VehicleLifecycleStatus lifecycleBefore = vehicle.getLifecycleStatus();
        vehicle.changeStatusTo(target);
        vehicleRepository.save(vehicle);
        log.info("Vehicle status updated vehicle={} status={}", id, target);

        // Retiring via the operational status also retires the vehicle in its lifecycle
        // (see Vehicle.changeStatus) — record that in the lifecycle history too.
        if (vehicle.getLifecycleStatus() != lifecycleBefore) {
            lifecycleEventRepository.save(VehicleLifecycleEvent.record(tenantId, id,
                    LifecycleEventType.STATUS_CHANGE, lifecycleBefore, vehicle.getLifecycleStatus(),
                    java.time.Instant.now(), "Vehicle status set to " + target,
                    null, UserContext.getCurrentUserId()));
        }

        if (target == VehicleStatus.BREAKDOWN) {
            notifyBreakdown(tenantId, vehicle);
        }
        return toResponse(vehicle);
    }

    @Transactional
    public void deleteVehicle(TenantId tenantId, UUID id, UUID deletedByUserId) {
        Vehicle vehicle = findActive(tenantId, id);
        // FIX: previously always called softDelete(null), losing the audit
        // trail of who deleted the vehicle. Same bug, same fix, as
        // earthmoving's original EarthAssetService.deleteAsset().
        vehicle.softDelete(deletedByUserId);
        vehicleRepository.save(vehicle);
    }

    // ── Services ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<ServiceResponse> getServiceHistory(TenantId tenantId, UUID vehicleId,
                                                   Pageable pageable) {
        findActive(tenantId, vehicleId);
        return serviceRepository.findByVehicle(vehicleId, pageable)
                .map(this::toServiceResponse);
    }

    @Transactional
    public ServiceResponse recordService(TenantId tenantId, UUID vehicleId,
                                         CreateServiceRequest req) {
        Vehicle vehicle = findActive(tenantId, vehicleId);

        VehicleService svc = VehicleService.create(
                tenantId, vehicleId,
                req.type(), req.description(), req.serviceDate(),
                req.odometerAtService(), req.nextServiceKm(),
                req.cost(), req.supplier(), req.invoiceRef()
        );
        serviceRepository.save(svc);

        if (req.odometerAtService() != null) {
            // recordService() now also stamps lastServiceDate — see Vehicle's
            // Javadoc on isDueForService() for why that's the fix that makes
            // the day-based interval actually work.
            vehicle.recordService(req.odometerAtService());
            if (req.odometerAtService() > (vehicle.getCurrentOdometer() != null
                    ? vehicle.getCurrentOdometer() : 0)) {
                vehicle.updateOdometer(req.odometerAtService());
            }
            vehicleRepository.save(vehicle);
        }

        log.info("Service recorded vehicle={} type={}", vehicleId, req.type());
        return toServiceResponse(svc);
    }

    // ── Trips ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<TripResponse> getAllTrips(TenantId tenantId, String status,
                                          Pageable pageable) {
        if (status != null && !status.isBlank())
            return tripRepository.findAllActiveByStatus(tenantId, status.toUpperCase(), pageable)
                    .map(t -> toTripResponseWithReg(t, tenantId));
        return tripRepository.findAllActive(tenantId, pageable)
                .map(t -> toTripResponseWithReg(t, tenantId));
    }

    @Transactional(readOnly = true)
    public Page<TripResponse> getTrips(TenantId tenantId, UUID vehicleId,
                                       Pageable pageable) {
        findActive(tenantId, vehicleId);
        return tripRepository.findByVehicle(vehicleId, pageable)
                .map(this::toTripResponse);
    }

    @Transactional
    public TripResponse startTrip(TenantId tenantId, UUID vehicleId,
                                  StartTripRequest req) {
        Vehicle vehicle = findActive(tenantId, vehicleId);

        // NEW (chunk 5): a stolen, impounded, sold, written-off... vehicle cannot be put to work.
        if (!vehicle.getLifecycleStatus().isOperating()) {
            throw new IllegalStateException("Vehicle lifecycle status is " + vehicle.getLifecycleStatus()
                    + " — only ACTIVE vehicles can start a trip.");
        }

        // A vehicle that cannot be driven after an accident stays off the road until the case is closed or corrected.
        String undrivable = DrivabilityRules.problem(accidentRepository.findUndrivableOpen(tenantId, vehicleId));
        if (undrivable != null) throw new IllegalStateException(undrivable);

        // NEW (chunk 6): optional link to a Driver record. Only trips linked by driverId count
        // towards that driver's score. When a driver is given we also refuse to dispatch an
        // inactive driver, or one whose licence (or required PrDP) has already expired.
        final UUID driverId = req.driverId();
        String driverName = req.driverName();
        if (driverId != null) {
            Driver driver = driverRepository.findActiveById(tenantId, driverId)
                    .orElseThrow(() -> new ResourceNotFoundException("Driver", driverId.toString()));
            if (!"ACTIVE".equals(driver.getStatus())) {
                throw new IllegalArgumentException("Driver " + driver.getFullName() + " is inactive");
            }
            java.time.LocalDate today = java.time.LocalDate.now();
            if (driver.getLicenseExpiry() != null && driver.getLicenseExpiry().isBefore(today)) {
                throw new IllegalArgumentException("Driver " + driver.getFullName()
                        + "'s driving licence expired on " + driver.getLicenseExpiry());
            }
            if (driver.isPrdpRequired() && driver.getPrdpExpiry() != null && driver.getPrdpExpiry().isBefore(today)) {
                throw new IllegalArgumentException("Driver " + driver.getFullName()
                        + "'s PrDP expired on " + driver.getPrdpExpiry());
            }
            if (driverName == null || driverName.isBlank()) {
                driverName = driver.getFullName();
            }
        }

        tripRepository.findActiveTrip(vehicleId).ifPresent(t -> {
            throw new IllegalStateException(
                    "Vehicle already has an active trip. End the current trip first.");
        });

        // NOTE: throws InvalidVehicleStatusTransitionException if the vehicle
        // isn't currently AVAILABLE — the original code let you start a trip
        // on a vehicle that was mid-service or already on another trip
        // (the findActiveTrip check above caught the "another trip" case,
        // but not "vehicle is in MAINTENANCE/BREAKDOWN/RETIRED").
        vehicle.startTripStatus();
        vehicleRepository.save(vehicle);

        Trip trip = Trip.create(
                tenantId, vehicleId,
                req.guardId(), driverId, driverName, req.purpose(),
                req.tripType() != null ? req.tripType() : "BUSINESS",
                req.startLocation(),
                req.startOdometer(), req.startAt()
        );
        tripRepository.save(trip);
        log.info("Trip started vehicle={} driver={}", vehicleId, driverName);
        return toTripResponse(trip);
    }

    @Transactional
    public TripResponse endTrip(TenantId tenantId, UUID vehicleId,
                                EndTripRequest req) {
        Vehicle vehicle = findActive(tenantId, vehicleId);

        Trip trip = tripRepository.findActiveTrip(vehicleId)
                .orElseThrow(() -> new IllegalStateException(
                        "No active trip found for this vehicle"));

        trip.complete(req.endLocation(), req.endOdometer(),
                req.endAt(), req.fuelUsedLitres(), req.notes());
        tripRepository.save(trip);

        boolean wasDue = vehicle.isDueForService();
        if (req.endOdometer() != null) {
            // FIX: previously called unconditionally with no floor check —
            // now guarded inside Vehicle.updateOdometer() itself (throws if
            // lower than current), consistent with every other odometer
            // write path in this service.
            vehicle.updateOdometer(req.endOdometer());
        }
        vehicle.endTripStatus();
        vehicleRepository.save(vehicle);

        // Only notify on the transition into "due", not every time the
        // odometer advances while already due — same reasoning as
        // earthmoving's EarthAssetService.updateHours().
        if (!wasDue && vehicle.isDueForService()) {
            notifyServiceDue(tenantId, vehicle);
        }

        log.info("Trip ended vehicle={} distance={}km", vehicleId, trip.getDistanceKm());
        return toTripResponse(trip);
    }

    // ── Fuel fill-ups ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<FuelFillupResponse> getFuelLog(TenantId tenantId, UUID vehicleId,
                                               Pageable pageable) {
        findActive(tenantId, vehicleId);
        return fuelFillupRepository.findByVehicle(vehicleId, pageable)
                .map(this::toFuelResponse);
    }

    @Transactional
    public FuelFillupResponse logFuel(TenantId tenantId, UUID vehicleId,
                                      LogFuelRequest req) {
        Vehicle vehicle = findActive(tenantId, vehicleId);

        BigDecimal cost = req.totalCost();
        if (cost == null && req.litres() != null && req.pricePerLitre() != null) {
            cost = req.litres().multiply(req.pricePerLitre());
        }

        FuelFillup fillup = FuelFillup.create(
                tenantId, vehicleId,
                req.filledAt(), req.litres(), req.pricePerLitre(), cost,
                req.odometerAtFillup(), req.station(), req.receiptRef(), req.fullTank()
        );
        fuelFillupRepository.save(fillup);

        boolean wasDue = vehicle.isDueForService();
        if (req.odometerAtFillup() != null
                && req.odometerAtFillup() > (vehicle.getCurrentOdometer() != null
                ? vehicle.getCurrentOdometer() : 0)) {
            vehicle.updateOdometer(req.odometerAtFillup());
            vehicleRepository.save(vehicle);
        }
        if (!wasDue && vehicle.isDueForService()) {
            notifyServiceDue(tenantId, vehicle);
        }

        log.info("Fuel logged vehicle={} litres={}", vehicleId, req.litres());
        return toFuelResponse(fillup);
    }

    void notifyServiceDue(TenantId tenantId, Vehicle vehicle) {
        List<Recipient> recipients = tenantAdminRecipients.resolveTenantAdmins(tenantId);
        if (recipients.isEmpty()) return;

        notificationService.send(NotificationRequest.builder()
                .tenantId(tenantId)
                .type(NotificationType.VEHICLE_SERVICE_DUE)
                .title("Service due: " + vehicle.getRegistration())
                .message(vehicle.getRegistration() + " has reached " + vehicle.getCurrentOdometer()
                        + " km and is due for service (interval: " + vehicle.getServiceIntervalKm() + " km).")
                .actionUrl("/fleet/vehicles/" + vehicle.getId())
                .sourceModule("fleet")
                .sourceEntityId(vehicle.getId().toString())
                .recipients(recipients)
                .build());
    }

    // ── Notifications ─────────────────────────────────────────────────────
    // Compliance-expiry and long-running-trip alerts live in
    // FleetNotificationScheduler (they're time-driven, not event-driven —
    // nothing "happens" to trigger them, a clock does). This section is only
    // for alerts triggered by an actual state change within a request.

    private void notifyBreakdown(TenantId tenantId, Vehicle vehicle) {
        List<Recipient> recipients = tenantAdminRecipients.resolveTenantAdmins(tenantId);
        if (recipients.isEmpty()) return;

        notificationService.send(NotificationRequest.builder()
                .tenantId(tenantId)
                .type(NotificationType.VEHICLE_BREAKDOWN)
                .title("Vehicle breakdown: " + vehicle.getRegistration())
                .message(vehicle.getRegistration() + " (" + vehicle.getMake() + " " + vehicle.getModel()
                        + ") was reported as broken down.")
                .actionUrl("/fleet/vehicles/" + vehicle.getId())
                .sourceModule("fleet")
                .sourceEntityId(vehicle.getId().toString())
                .recipients(recipients)
                .build());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Vehicle findActive(TenantId tenantId, UUID id) {
        return vehicleRepository.findActiveById(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle", id.toString()));
    }

    /**
     * Friendly pre-check; the partial unique indexes in the migration are the
     * real guarantee against a concurrent duplicate.
     */
    private void assertIdentifiersUnique(TenantId tenantId, UUID excludeId,
                                         String registration, String vin, String engineNumber) {
        if (vehicleRepository.existsOtherWithRegistration(tenantId, registration, excludeId)) {
            throw new IllegalArgumentException(
                    "Vehicle with registration " + registration + " already exists");
        }
        if (vin != null && vehicleRepository.existsOtherWithVin(tenantId, vin, excludeId)) {
            throw new IllegalArgumentException("Vehicle with VIN " + vin + " already exists");
        }
        if (engineNumber != null && vehicleRepository.existsOtherWithEngineNumber(tenantId, engineNumber, excludeId)) {
            throw new IllegalArgumentException("Vehicle with engine number " + engineNumber + " already exists");
        }
    }

    /** VIN / engine numbers are case-insensitive identifiers: trim, upper-case, blank → null. */
    private String normaliseIdentifier(String raw) {
        if (raw == null) return null;
        String v = raw.trim().toUpperCase();
        return v.isEmpty() ? null : v;
    }

    private String blankToNull(String raw) {
        if (raw == null) return null;
        String v = raw.trim();
        return v.isEmpty() ? null : v;
    }

    private VehicleStatus parseStatus(String raw) {
        try {
            return VehicleStatus.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown status: " + raw +
                    ". Valid values: AVAILABLE, ON_TRIP, MAINTENANCE, BREAKDOWN, RETIRED");
        }
    }

    private VehicleResponse toResponse(Vehicle v) {
        return new VehicleResponse(
                v.getId(), v.getRegistration(), v.getMake(), v.getModel(),
                v.getYear(), v.getColour(), v.getVehicleType(),
                v.getStatus().name(), v.getFuelType(),
                v.getLicenceDiscExpiry(), v.getRoadworthyExpiry(), v.getInsuranceExpiry(),
                v.getCurrentOdometer(), v.getLastServiceKm(), v.getServiceIntervalKm(),
                v.isDueForService(), v.isLicenceExpiringSoon(), v.isRoadworthyExpiringSoon(),
                v.getDailyRate(), v.getNotes(), v.getCreatedAt(),
                v.getAssignedDriverName(), v.getAssignedDriverId(),
                // NEW
                v.getVin(), v.getEngineNumber(), v.getVariant(), v.getBodyType(),
                v.getTransmission() != null ? v.getTransmission().name() : null,
                v.getDriveType() != null ? v.getDriveType().name() : null,
                v.getAxleCount(), v.getGvmKg(), v.getTareKg(), v.getPayloadKg(), v.getGcmKg(),
                v.getEngineHours(), v.getTankCapacityLitres(),
                v.getServiceIntervalDays(), v.getLastServiceDate(),
                v.getPurchaseDate(),
                v.getAcquisitionMethod() != null ? v.getAcquisitionMethod().name() : null,
                v.getPurchasePrice(), v.getCurrentBookValue(), v.getCurrentEstimatedValue(),
                v.getValuedAt(),
                v.getLifecycleStatus().name()
        );
    }

    /** Pass null driverId to unassign. */
    @Transactional
    public VehicleResponse assignDriver(TenantId tenantId, UUID vehicleId, UUID driverId) {
        Vehicle vehicle = findActive(tenantId, vehicleId);
        vehicle.assignDriver(driverId);
        vehicleRepository.save(vehicle);
        log.info("Driver assignment updated vehicle={} driver={}", vehicleId, driverId);
        return toResponse(vehicle);
    }

    private ServiceResponse toServiceResponse(VehicleService s) {
        return new ServiceResponse(
                s.getId(), s.getVehicleId(), s.getType(), s.getDescription(),
                s.getServiceDate(), s.getOdometerAtService(),
                s.getNextServiceKm(),
                s.getCost(), s.getSupplier(),
                s.getInvoiceRef(),
                s.getCreatedAt()
        );
    }

    private TripResponse toTripResponse(Trip t) {
        return new TripResponse(
                t.getId(), t.getVehicleId(), null,
                t.getDriverName(), t.getPurpose(), t.getTripType(),
                t.getStartLocation(), t.getEndLocation(),
                t.getStartOdometer(), t.getEndOdometer(), t.getDistanceKm(),
                t.getStartAt(), t.getEndAt(), t.getFuelUsedLitres(),
                t.getStatus(), t.getNotes(), t.getCreatedAt(), t.getDriverId()
        );
    }

    private TripResponse toTripResponseWithReg(Trip t, TenantId tenantId) {
        String reg = vehicleRepository.findActiveById(tenantId, t.getVehicleId())
                .map(Vehicle::getRegistration).orElse(null);
        return new TripResponse(
                t.getId(), t.getVehicleId(), reg,
                t.getDriverName(), t.getPurpose(), t.getTripType(),
                t.getStartLocation(), t.getEndLocation(),
                t.getStartOdometer(), t.getEndOdometer(), t.getDistanceKm(),
                t.getStartAt(), t.getEndAt(), t.getFuelUsedLitres(),
                t.getStatus(), t.getNotes(), t.getCreatedAt(), t.getDriverId()
        );
    }

    private FuelFillupResponse toFuelResponse(FuelFillup f) {
        return new FuelFillupResponse(
                f.getId(), f.getVehicleId(),
                f.getFilledAt(), f.getLitres(), f.getPricePerLitre(), f.getTotalCost(),
                f.getOdometerAtFillup(), f.getStation(), f.getReceiptRef(),
                f.isFullTank(), f.getCreatedAt()
        );
    }
}