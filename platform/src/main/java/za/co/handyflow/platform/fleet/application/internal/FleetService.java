package za.co.handyflow.platform.fleet.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.fleet.domain.model.FuelFillup;
import za.co.handyflow.platform.fleet.domain.model.Trip;
import za.co.handyflow.platform.fleet.domain.model.Vehicle;
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
    private final NotificationService      notificationService;
    // Same shared port earthmoving uses — see TenantAdminRecipientsImpl in
    // the identity module. No fleet-specific recipients port; that lesson
    // was already learned building earthmoving's equivalent.
    private final TenantAdminRecipients    tenantAdminRecipients;

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
        if (vehicleRepository.existsByTenantIdAndRegistrationAndDeletedAtIsNull(
                tenantId, req.registration().toUpperCase())) {
            throw new IllegalArgumentException(
                    "Vehicle with registration " + req.registration() + " already exists");
        }

        Vehicle vehicle = Vehicle.create(
                tenantId,
                req.registration().toUpperCase(),
                req.make(), req.model(), req.year(),
                req.colour(), req.vin(),
                req.vehicleType(), req.fuelType(),
                req.licenceDiscExpiry(), req.roadworthyExpiry(), req.insuranceExpiry(),
                req.dailyRate(), req.tankCapacityLitres(),
                req.serviceIntervalKm() != null ? req.serviceIntervalKm() : 10000,
                req.serviceIntervalDays(),
                req.assignedDriverName(),
                req.notes()
        );

        vehicleRepository.save(vehicle);
        log.info("Registered vehicle={} tenant={}", vehicle.getRegistration(), tenantId);
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
        vehicle.changeStatusTo(target);
        vehicleRepository.save(vehicle);
        log.info("Vehicle status updated vehicle={} status={}", id, target);

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
                req.guardId(), req.driverName(), req.purpose(),
                req.tripType() != null ? req.tripType() : "BUSINESS",
                req.startLocation(),
                req.startOdometer(), req.startAt()
        );
        tripRepository.save(trip);
        log.info("Trip started vehicle={} driver={}", vehicleId, req.driverName());
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
                v.getAssignedDriverName(), v.getAssignedDriverId()
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
                t.getStatus(), t.getNotes(), t.getCreatedAt()
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
                t.getStatus(), t.getNotes(), t.getCreatedAt()
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
