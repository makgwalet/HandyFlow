package za.co.handyflow.platform.fleet.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fleet.domain.model.VehicleWriteOff;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

public interface VehicleWriteOffRepository extends JpaRepository<VehicleWriteOff, UUID> {

    /** Newest first — the first element is the current write-off. */
    @Query("SELECT w FROM VehicleWriteOff w WHERE w.tenantId = :tenantId AND w.vehicleId = :vehicleId " +
            "ORDER BY w.createdAt DESC")
    List<VehicleWriteOff> findByVehicle(TenantId tenantId, UUID vehicleId);
}