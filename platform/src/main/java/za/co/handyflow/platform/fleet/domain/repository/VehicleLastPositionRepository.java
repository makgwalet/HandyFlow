package za.co.handyflow.platform.fleet.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fleet.domain.model.VehicleLastPosition;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface VehicleLastPositionRepository extends JpaRepository<VehicleLastPosition, UUID> {

    @Query("SELECT p FROM VehicleLastPosition p WHERE p.tenantId = :tenantId ORDER BY p.recordedAt DESC")
    List<VehicleLastPosition> findAllForTenant(TenantId tenantId);

    @Query("SELECT p FROM VehicleLastPosition p WHERE p.vehicleId IN :vehicleIds")
    List<VehicleLastPosition> findByVehicleIds(Collection<UUID> vehicleIds);
}