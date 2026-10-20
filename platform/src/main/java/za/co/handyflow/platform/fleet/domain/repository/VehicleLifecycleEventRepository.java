package za.co.handyflow.platform.fleet.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fleet.domain.model.VehicleLifecycleEvent;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VehicleLifecycleEventRepository extends JpaRepository<VehicleLifecycleEvent, UUID> {

    @Query("SELECT e FROM VehicleLifecycleEvent e WHERE e.tenantId = :tenantId AND e.vehicleId = :vehicleId " +
            "ORDER BY e.occurredAt DESC, e.recordedAt DESC")
    List<VehicleLifecycleEvent> findByVehicle(TenantId tenantId, UUID vehicleId);

    @Query("SELECT e FROM VehicleLifecycleEvent e WHERE e.tenantId = :tenantId AND e.vehicleId = :vehicleId AND e.id = :id")
    Optional<VehicleLifecycleEvent> findByVehicleAndId(TenantId tenantId, UUID vehicleId, UUID id);
}