// fleet/domain/repository/VehicleServiceRepository.java

package za.co.handyflow.platform.fleet.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fleet.domain.model.VehicleService;

import java.util.UUID;

public interface VehicleServiceRepository extends JpaRepository<VehicleService, UUID> {

    @Query("SELECT s FROM VehicleService s WHERE s.vehicleId = :vehicleId AND s.deletedAt IS NULL ORDER BY s.serviceDate DESC")
    Page<VehicleService> findByVehicle(UUID vehicleId, Pageable pageable);
}