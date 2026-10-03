package za.co.handyflow.platform.fleet.application;

import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Read-only view of Fleet for other modules (ADR-001, W4). Agriculture reads a machine's operating rate through it to cost its use, and
 * snapshots the rate into its own ledger; it never writes to Fleet. Other modules never touch Fleet's repositories or entities.
 */
public interface FleetFacade {

    /**
     * @param operatingRatePerHour what an hour of use costs to keep the machine running (service and repairs only, not fuel, not depreciation);
     *                             null until someone sets it
     * @param engineHours          the engine-hours meter, null until recorded
     */
    record EquipmentSummary(UUID id, String registration, String make, String model, String vehicleType, BigDecimal engineHours, BigDecimal operatingRatePerHour) {}

    /** One vehicle, or empty if it does not exist for this tenant (or was deleted). */
    Optional<EquipmentSummary> findEquipment(TenantId tenantId, UUID vehicleId);

    /** The tenant's active vehicles that are not retired, by registration. */
    List<EquipmentSummary> listEquipment(TenantId tenantId);
}
