// fleet/application/internal/FleetService.java

package za.co.handyflow.platform.fleet.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.fleet.domain.model.Trip;
import za.co.handyflow.platform.fleet.domain.model.Vehicle;
import za.co.handyflow.platform.fleet.domain.model.VehicleService;
import za.co.handyflow.platform.fleet.domain.repository.*;
import za.co.handyflow.platform.fleet.dto.*;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FleetService {

    private final VehicleRepository        vehicleRepository;
    private final VehicleServiceRepository serviceRepository;
    private final TripRepository           tripRepository;

    // ── Vehicles ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<VehicleResponse> getVehicles(TenantId tenantId, String status,
                                             Pageable pageable) {
        var page = (status == null || status.isBlank())
                ? vehicleRepository.findAllActive(tenantId, pageable)
                : vehicleRepository.findByStatus(tenantId, status.toUpperCase(), pageable);
        return page.map(this::toResponse);
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
                    "Vehicle with registration " + req.registration() + " already exists"
            );
        }
        Vehicle vehicle = Vehicle.create(tenantId, req.registration(), req.make(),
                req.model(), req.year(), req.vehicleType(), req.fuelType(),
                req.licenceDiscExpiry());

        vehicleRepository.save(vehicle);
        log.info("Created vehicle={} tenant={}", vehicle.getRegistration(), tenantId);
        return toResponse(vehicle);
    }

    @Transactional
    public VehicleResponse updateStatus(TenantId tenantId, UUID id,
                                        UpdateVehicleStatusRequest req) {
        Vehicle vehicle = findActive(tenantId, id);
        vehicle.updateStatus(req.status().toUpperCase());
        vehicleRepository.save(vehicle);
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

        VehicleService svc = VehicleService.create(tenantId, vehicleId,
                req.type(), req.description(), req.serviceDate(),
                req.odometerAtService(), req.cost(), req.supplier(), req.invoiceRef());
        serviceRepository.save(svc);

        // Update odometer and last service
        if (req.odometerAtService() != null) {
            vehicle.recordService(req.odometerAtService());
            if (req.odometerAtService() > vehicle.getCurrentOdometer()) {
                vehicle.updateOdometer(req.odometerAtService());
            }
            vehicleRepository.save(vehicle);
        }
        return toServiceResponse(svc);
    }

    // ── Trips ─────────────────────────────────────────────────────────────────

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

        // WHY check? Cannot start a new trip if vehicle is already on one
        tripRepository.findActiveTrip(vehicleId).ifPresent(t -> {
            throw new IllegalStateException(
                    "Vehicle already has an active trip. End the current trip first."
            );
        });

        vehicle.updateStatus("IN_USE");
        vehicleRepository.save(vehicle);

        Trip trip = Trip.create(tenantId, vehicleId, req.guardId(),
                req.driverName(), req.purpose(), req.startLocation(),
                req.startOdometer(), req.startAt());
        tripRepository.save(trip);
        return toTripResponse(trip);
    }

    @Transactional
    public TripResponse endTrip(TenantId tenantId, UUID vehicleId, EndTripRequest req) {
        Vehicle vehicle = findActive(tenantId, vehicleId);

        Trip trip = tripRepository.findActiveTrip(vehicleId)
                .orElseThrow(() -> new IllegalStateException(
                        "No active trip found for this vehicle"
                ));

        trip.complete(req.endLocation(), req.endOdometer(),
                req.endAt(), req.fuelUsedLitres(), req.notes());
        tripRepository.save(trip);

        if (req.endOdometer() != null) {
            vehicle.updateOdometer(req.endOdometer());
        }
        vehicle.updateStatus("AVAILABLE");
        vehicleRepository.save(vehicle);

        return toTripResponse(trip);
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
                s.getCost(), s.getSupplier(), s.getCreatedAt()
        );
    }

    private TripResponse toTripResponse(Trip t) {
        return new TripResponse(
                t.getId(), t.getVehicleId(), t.getDriverName(), t.getPurpose(),
                t.getStartLocation(), t.getEndLocation(),
                t.getStartOdometer(), t.getEndOdometer(), t.getDistanceKm(),
                t.getStartAt(), t.getEndAt(), t.getFuelUsedLitres(), t.getCreatedAt()
        );
    }
}