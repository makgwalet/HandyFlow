package za.co.handyflow.platform.fuel;

import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * What Fuel needs to know about a vehicle in Fleet, defined here and implemented by Fleet.
 * <p>
 * Fleet already depends on Fuel (it consumes Fuel's dispatch event), so Fuel cannot depend on Fleet without a module cycle.
 * Turning the dependency around keeps it one-directional: Fuel asks this interface, Fleet answers it. Every call is for one tenant
 * and can only return that tenant's vehicles. If Fleet is not present, no implementation exists and Fuel treats links as unavailable.
 */
public interface FleetVehicleLookup {

    record VehicleRef(UUID id, String registration, String make, String model) {
        public String label() {
            String name = ((make == null ? "" : make) + " " + (model == null ? "" : model)).trim();
            return name.isEmpty() ? registration : registration + " (" + name + ")";
        }
    }

    Optional<VehicleRef> find(TenantId tenantId, UUID vehicleId);

    /** The tenant's vehicles that still exist, newest data first is not guaranteed. */
    List<VehicleRef> list(TenantId tenantId);
}
