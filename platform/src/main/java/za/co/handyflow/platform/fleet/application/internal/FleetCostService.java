package za.co.handyflow.platform.fleet.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.fleet.domain.model.Vehicle;
import za.co.handyflow.platform.fleet.domain.repository.FuelFillupRepository;
import za.co.handyflow.platform.fleet.domain.repository.VehicleRepository;
import za.co.handyflow.platform.fleet.domain.repository.VehicleServiceRepository;
import za.co.handyflow.platform.fleet.dto.VehicleCostSummaryResponse;
import za.co.handyflow.platform.fleet.domain.repository.TripRepository;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Cost-per-km — flagged in the gap analysis as "small lift, high value":
 * service cost + fuel cost + trip distance were all already captured, just
 * never joined together into the one number that actually answers "what
 * does this vehicle cost to run".
 * <p>
 * SCOPE: all-time totals, not date-ranged. A vehicle's cost-per-km is
 * mostly useful as a comparison between vehicles (which one is expensive to
 * run) rather than a period metric — if a monthly/annual breakdown turns
 * out to matter later, this is the file to extend with a from/to range,
 * following the same pattern as FleetLogbookService's date handling.
 */
@Service
@RequiredArgsConstructor
public class FleetCostService {

    private final VehicleRepository vehicleRepository;
    private final VehicleServiceRepository serviceRepository;
    private final FuelFillupRepository fuelFillupRepository;
    private final TripRepository tripRepository;

    @Transactional(readOnly = true)
    public VehicleCostSummaryResponse getCostSummary(TenantId tenantId, UUID vehicleId) {
        Vehicle vehicle = vehicleRepository.findActiveById(tenantId, vehicleId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle", vehicleId.toString()));
        return summarize(vehicle);
    }

    @Transactional(readOnly = true)
    public List<VehicleCostSummaryResponse> getFleetCostSummary(TenantId tenantId) {
        // size=1000 is a pragmatic ceiling, not a real pagination story —
        // this is a dashboard rollup over the whole fleet, and a tenant with
        // more than 1000 vehicles needing this to be properly paginated is
        // not the common case this endpoint was built for.
        List<Vehicle> vehicles = vehicleRepository
                .findAllActive(tenantId, org.springframework.data.domain.Pageable.ofSize(1000))
                .getContent();
        return vehicles.stream()
                .map(this::summarize)
                .sorted(Comparator.comparing(
                        VehicleCostSummaryResponse::costPerKm,
                        Comparator.nullsLast(Comparator.reverseOrder()))) // most expensive per km first
                .toList();
    }

    private VehicleCostSummaryResponse summarize(Vehicle vehicle) {
        BigDecimal serviceCost = serviceRepository.sumCostByVehicle(vehicle.getId());
        BigDecimal fuelCost = fuelFillupRepository.sumCostByVehicle(vehicle.getId());
        BigDecimal totalCost = serviceCost.add(fuelCost);
        int totalKm = tripRepository.sumDistanceKmByVehicle(vehicle.getId());

        BigDecimal costPerKm = totalKm > 0
                ? totalCost.divide(BigDecimal.valueOf(totalKm), 2, RoundingMode.HALF_UP)
                : null; // null, not zero — "no data yet" is a different fact from "free to run"

        return new VehicleCostSummaryResponse(
                vehicle.getId(), vehicle.getRegistration(), vehicle.getMake(), vehicle.getModel(),
                serviceCost, fuelCost, totalCost, totalKm, costPerKm
        );
    }
}
