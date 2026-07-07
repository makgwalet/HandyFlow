package za.co.handyflow.platform.fleet.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fleet.domain.model.FuelFillup;

import java.math.BigDecimal;
import java.util.UUID;

public interface FuelFillupRepository extends JpaRepository<FuelFillup, UUID> {

    @Query("SELECT f FROM FuelFillup f WHERE f.vehicleId = :vehicleId ORDER BY f.filledAt DESC")
    Page<FuelFillup> findByVehicle(UUID vehicleId, Pageable pageable);

    // NEW: backs the cost-per-km feature — see VehicleServiceRepository
    // .sumCostByVehicle for why COALESCE matters here too.
    @Query("SELECT COALESCE(SUM(f.totalCost), 0) FROM FuelFillup f WHERE f.vehicleId = :vehicleId")
    BigDecimal sumCostByVehicle(UUID vehicleId);
}
