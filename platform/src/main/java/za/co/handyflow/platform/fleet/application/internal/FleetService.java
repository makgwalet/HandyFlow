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
import za.co.handyflow.platform.fleet.domain.repository.*;
import za.co.handyflow.platform.fleet.dto.*;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FleetService {

    private final VehicleRepository        vehicleRepository;
    private final VehicleServiceRepository serviceRepository;
    private final TripRepository           tripRepository;
    private final FuelFillupRepository     fuelFillupRepository;

    // ── Vehicles ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<VehicleResponse> getVehicles(TenantId tenantId, String status,
                                             String vehicleType, Pageable pageable) {
        // Split queries — avoid IS NULL OR pattern (PostgreSQL null parameter bug)
        boolean hasStatus = status != null && !status.isBlank();
        boolean hasType   = vehicleType != null && !vehicleType.isBlank();

        if (!hasStatus && !hasType)
            return vehicleRepository.findAllActive(tenantId, pageable).map(this::toResponse);
        if (hasStatus && !hasType)
            return vehicleRepository.findByStatus(tenantId, status.toUpperCase(), pageable).map(this::toResponse);
        if (!hasStatus)
            return vehicleRepository.findByType(tenantId, vehicleType.toUpperCase(), pageable).map(this::toResponse);
        return vehicleRepository.findByStatusAndType(tenantId, status.toUpperCase(),
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

        // Updated: pass all new fields from the extended CreateVehicleRequest
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
    public VehicleResponse updateStatus(TenantId tenantId, UUID id,
                                        UpdateVehicleStatusRequest req) {
        Vehicle vehicle = findActive(tenantId, id);
        vehicle.updateStatus(req.status().toUpperCase());
        vehicleRepository.save(vehicle);
        log.info("Vehicle status updated vehicle={} status={}", id, req.status());
        return toResponse(vehicle);
    }

    @Transactional
    public void deleteVehicle(TenantId tenantId, UUID id) {
        Vehicle vehicle = findActive(tenantId, id);
        vehicle.softDelete(null);
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

        // Update last service odometer and reset service interval clock
        if (req.odometerAtService() != null) {
            vehicle.recordService(req.odometerAtService());
            // Also advance current odometer if service reading is higher
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

        // Block double trips
        tripRepository.findActiveTrip(vehicleId).ifPresent(t -> {
            throw new IllegalStateException(
                    "Vehicle already has an active trip. End the current trip first.");
        });

        // Use ON_TRIP status (not IN_USE — consistent with frontend and VehicleResponse)
        vehicle.updateStatus("ON_TRIP");
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

        if (req.endOdometer() != null) {
            vehicle.updateOdometer(req.endOdometer());
        }
        vehicle.updateStatus("AVAILABLE");
        vehicleRepository.save(vehicle);

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

        // Compute totalCost if not provided but litres + pricePerLitre are
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

        // Advance odometer if fill-up reading is higher than current
        if (req.odometerAtFillup() != null
                && req.odometerAtFillup() > (vehicle.getCurrentOdometer() != null
                ? vehicle.getCurrentOdometer() : 0)) {
            vehicle.updateOdometer(req.odometerAtFillup());
            vehicleRepository.save(vehicle);
        }

        log.info("Fuel logged vehicle={} litres={}", vehicleId, req.litres());
        return toFuelResponse(fillup);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Vehicle findActive(TenantId tenantId, UUID id) {
        return vehicleRepository.findActiveById(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle", id.toString()));
    }

    private VehicleResponse toResponse(Vehicle v) {
        return new VehicleResponse(
                v.getId(), v.getRegistration(), v.getMake(), v.getModel(),
                v.getYear(), v.getColour(), v.getVehicleType(),
                v.getStatus(), v.getFuelType(),
                v.getLicenceDiscExpiry(), v.getRoadworthyExpiry(), v.getInsuranceExpiry(),
                v.getCurrentOdometer(), v.getLastServiceKm(), v.getServiceIntervalKm(),
                v.isDueForService(), v.isLicenceExpiringSoon(), v.isRoadworthyExpiringSoon(),
                v.getDailyRate(), v.getNotes(), v.getCreatedAt()
        );
    }

    private ServiceResponse toServiceResponse(VehicleService s) {
        return new ServiceResponse(
                s.getId(), s.getVehicleId(), s.getType(), s.getDescription(),
                s.getServiceDate(), s.getOdometerAtService(),
                s.getNextServiceKm(),          // newly added field
                s.getCost(), s.getSupplier(),
                s.getInvoiceRef(),             // newly added field
                s.getCreatedAt()
        );
    }

    private TripResponse toTripResponse(Trip t) {
        return new TripResponse(
                t.getId(), t.getVehicleId(),
                null,                          // registration — not available without join
                t.getDriverName(), t.getPurpose(),
                t.getTripType(),               // newly added field
                t.getStartLocation(), t.getEndLocation(),
                t.getStartOdometer(), t.getEndOdometer(), t.getDistanceKm(),
                t.getStartAt(), t.getEndAt(), t.getFuelUsedLitres(),
                t.getStatus(),                 // newly added field
                t.getNotes(),
                t.getCreatedAt()
        );
    }

    // Global trip list — denormalize registration from vehicle lookup
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
