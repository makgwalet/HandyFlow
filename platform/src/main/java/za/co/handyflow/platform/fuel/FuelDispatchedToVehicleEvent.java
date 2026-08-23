package za.co.handyflow.platform.fuel;

import za.co.handyflow.platform.shared.DomainEvent;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * FIX: backlog 5.1 — Fuel and Fleet had no shared write path or
 * reconciling read; fuel a vehicle received from the company's own tank
 * via dispatchFuel() never reached FleetCostService's cost-per-km
 * calculation, which only ever read fleet.FuelFillupRepository. Per
 * your own confirmed decision (event-driven, matching the established
 * EmployeeCreatedEvent→ContractingHrEventHandler / InvoiceIssuedEvent→
 * InvoicingAccountingEventHandler pattern), published here and consumed
 * by a new listener in fleet — fleet already needs no reverse
 * dependency risk (confirmed: fuel doesn't depend on fleet, so fleet
 * depending on fuel is a clean one-directional edge).
 * <p>
 * Only published when the dispatch actually went to a vehicle
 * (FuelDispatch.vehicleId != null) — dispatches to assets or external
 * customers have nothing to do with Fleet's cost-per-km and correctly
 * publish nothing.
 */
public record FuelDispatchedToVehicleEvent(
        TenantId tenantId,
        UUID dispatchId,
        UUID vehicleId,
        BigDecimal litresDispensed,
        BigDecimal pricePerLitre,
        Instant dispatchedAt,
        Integer odometerReading,
        Instant occurredOn
) implements DomainEvent {

    public static FuelDispatchedToVehicleEvent of(TenantId tenantId, UUID dispatchId, UUID vehicleId,
                                                  BigDecimal litresDispensed, BigDecimal pricePerLitre,
                                                  Instant dispatchedAt, Integer odometerReading) {
        return new FuelDispatchedToVehicleEvent(tenantId, dispatchId, vehicleId, litresDispensed,
                pricePerLitre, dispatchedAt, odometerReading, Instant.now());
    }
}