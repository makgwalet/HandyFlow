package za.co.handyflow.platform.fuel.application;

import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Read-only view of Fuel for other modules (ADR-001, W4). Agriculture reads the tenant's OWN fuel dispatches through it and snapshots each
 * dispatch's cost into its cost ledger; it never writes to Fuel. A dispatch to a customer is a sale and is never offered here.
 */
public interface FuelFacade {

    /**
     * One dispatch of the tenant's own fuel to its own vehicle or asset.
     *
     * @param costPerLitre the tank's weighted-average cost per litre when the fuel was dispensed (Fuel's own snapshot); null on older rows
     */
    record OwnDispatch(UUID id, Instant dispatchedAt, UUID tankId, String tankName, UUID vehicleId, UUID assetId, String recipientName,
                       BigDecimal litres, BigDecimal costPerLitre, BigDecimal hoursReading) {}

    /** Own dispatches in [from, to), newest first; {@code maxRows} caps the result (at most 1000). */
    List<OwnDispatch> findOwnDispatches(TenantId tenantId, Instant from, Instant to, int maxRows);

    /** One own dispatch, or empty if it does not exist, was deleted, or was a sale to a customer. */
    Optional<OwnDispatch> findOwnDispatch(TenantId tenantId, UUID dispatchId);
}
