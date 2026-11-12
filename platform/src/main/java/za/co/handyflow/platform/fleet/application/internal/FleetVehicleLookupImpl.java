package za.co.handyflow.platform.fleet.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.fleet.domain.model.Vehicle;
import za.co.handyflow.platform.fleet.domain.repository.VehicleRepository;
import za.co.handyflow.platform.fuel.FleetVehicleLookup;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Answers Fuel's questions about the tenant's own vehicles. Read-only; Fleet stays the only owner of vehicle data. */
@Component
@RequiredArgsConstructor
class FleetVehicleLookupImpl implements FleetVehicleLookup {

    /** More than any one tenant should have as vehicles; keeps the list bounded. */
    private static final int MAX = 2000;

    private final VehicleRepository vehicles;

    @Override
    @Transactional(readOnly = true)
    public Optional<VehicleRef> find(TenantId tenantId, UUID vehicleId) {
        return vehicles.findActiveById(tenantId, vehicleId).map(FleetVehicleLookupImpl::ref);
    }

    @Override
    @Transactional(readOnly = true)
    public List<VehicleRef> list(TenantId tenantId) {
        return vehicles.findAllActive(tenantId, PageRequest.of(0, MAX)).stream().map(FleetVehicleLookupImpl::ref).toList();
    }

    private static VehicleRef ref(Vehicle v) {
        return new VehicleRef(v.getId(), v.getRegistration(), v.getMake(), v.getModel());
    }
}
