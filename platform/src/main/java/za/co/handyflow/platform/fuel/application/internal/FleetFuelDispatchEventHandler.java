package za.co.handyflow.platform.fleet.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.fleet.domain.model.FuelFillup;
import za.co.handyflow.platform.fleet.domain.model.Vehicle;
import za.co.handyflow.platform.fleet.domain.repository.FuelFillupRepository;
import za.co.handyflow.platform.fleet.domain.repository.VehicleRepository;
import za.co.handyflow.platform.fuel.FuelDispatchedToVehicleEvent;

import java.math.BigDecimal;

/**
 * FIX: backlog 5.1 — reconciles fuel dispatched from the Fuel module's
 * own tanks into Fleet's cost-per-km calculation. Lives inside fleet
 * (not fuel) specifically so it can import fuel's own published event
 * without creating a circular module dependency — confirmed neither
 * module currently depends on the other, so this direction is a clean,
 * newly-added one-directional edge (fleet → fuel), not a cycle. See
 * FuelDispatchedToVehicleEvent's own Javadoc for the full rationale.
 * <p>
 * Deliberately creates a REAL FuelFillup row rather than inventing any
 * new reconciliation mechanism — FleetCostService already sums
 * FuelFillup.totalCost via FuelFillupRepository.sumCostByVehicle(), so
 * a real row here flows into cost-per-km automatically with zero
 * changes needed to that calculation at all.
 * <p>
 * Mirrors FleetService.logFuel()'s FULL side-effect set (odometer
 * update if the dispatch's reading is newer, service-due notification
 * on the transition into "due") — not just the bare fuel record. Fuel
 * dispatched internally should produce exactly the same downstream
 * effects as fuel logged manually; anything less would make the two
 * paths behave inconsistently for no real reason.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class FleetFuelDispatchEventHandler {

    private final FuelFillupRepository fuelFillupRepo;
    private final VehicleRepository    vehicleRepo;
    private final FleetService         fleetService; // for notifyServiceDue() — widened to package-private for this

    @ApplicationModuleListener
    void onFuelDispatchedToVehicle(FuelDispatchedToVehicleEvent event) {
        try {
            // Idempotency: DB-level UNIQUE constraint on
            // source_fuel_dispatch_id is the real backstop; this check
            // just avoids an avoidable constraint-violation exception on
            // the (rare, but real under Spring Modulith's at-least-once
            // delivery) case of the same event arriving twice.
            if (fuelFillupRepo.findBySourceFuelDispatchId(event.dispatchId()).isPresent()) {
                log.info("[Fleet] Fuel dispatch={} already reconciled — skipping duplicate event", event.dispatchId());
                return;
            }

            Vehicle vehicle = vehicleRepo.findById(event.vehicleId()).orElse(null);
            if (vehicle == null) {
                log.warn("[Fleet] Fuel dispatch={} references vehicle={} not found in Fleet — not reconciled",
                        event.dispatchId(), event.vehicleId());
                return;
            }

            BigDecimal totalCost = event.pricePerLitre() != null
                    ? event.litresDispensed().multiply(event.pricePerLitre())
                    : null;

            FuelFillup fillup = FuelFillup.createFromFuelDispatch(
                    event.tenantId(), event.vehicleId(), event.dispatchId(),
                    event.dispatchedAt(), event.litresDispensed(), event.pricePerLitre(),
                    totalCost, event.odometerReading());
            fuelFillupRepo.save(fillup);

            // Same odometer-update logic as FleetService.logFuel().
            boolean wasDue = vehicle.isDueForService();
            if (event.odometerReading() != null
                    && event.odometerReading() > (vehicle.getCurrentOdometer() != null ? vehicle.getCurrentOdometer() : 0)) {
                vehicle.updateOdometer(event.odometerReading());
                vehicleRepo.save(vehicle);
            }
            if (!wasDue && vehicle.isDueForService()) {
                fleetService.notifyServiceDue(event.tenantId(), vehicle);
            }

            log.info("[Fleet] Reconciled fuel dispatch={} into vehicle={} fillup={} litres={}",
                    event.dispatchId(), event.vehicleId(), fillup.getId(), event.litresDispensed());
        } catch (Exception e) {
            // Same principle as every other cross-module side-effect
            // hookup this session: the dispatch is already saved and
            // committed in Fuel by the time this runs — a reconciliation
            // failure here must never look like it affected that.
            log.error("[Fleet] Failed to reconcile fuel dispatch={} for vehicle={}: {}",
                    event.dispatchId(), event.vehicleId(), e.getMessage(), e);
        }
    }
}