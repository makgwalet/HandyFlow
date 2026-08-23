package za.co.handyflow.platform.fleet.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fleet.domain.model.FuelFillup;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public interface FuelFillupRepository extends JpaRepository<FuelFillup, UUID> {

    @Query("SELECT f FROM FuelFillup f WHERE f.vehicleId = :vehicleId ORDER BY f.filledAt DESC")
    Page<FuelFillup> findByVehicle(UUID vehicleId, Pageable pageable);

    // NEW: backs the cost-per-km feature — see VehicleServiceRepository
    // .sumCostByVehicle for why COALESCE matters here too.
    @Query("SELECT COALESCE(SUM(f.totalCost), 0) FROM FuelFillup f WHERE f.vehicleId = :vehicleId")
    BigDecimal sumCostByVehicle(UUID vehicleId);

    /**
     * FIX: backlog 5.1 — lets FleetFuelDispatchEventHandler check
     * whether a fillup for this dispatch already exists before creating
     * a duplicate, as a first line of defense before the DB-level
     * UNIQUE constraint on source_fuel_dispatch_id would catch it anyway.
     */
    Optional<FuelFillup> findBySourceFuelDispatchId(UUID sourceFuelDispatchId);
}
