package za.co.handyflow.platform.fleet.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fleet.domain.model.Geofence;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GeofenceRepository extends JpaRepository<Geofence, UUID> {

    @Query("SELECT g FROM Geofence g WHERE g.tenantId = :tenantId AND g.deletedAt IS NULL ORDER BY g.name")
    List<Geofence> findAllLive(TenantId tenantId);

    @Query("SELECT g FROM Geofence g WHERE g.tenantId = :tenantId AND g.deletedAt IS NULL AND g.active = true")
    List<Geofence> findActive(TenantId tenantId);

    @Query("SELECT g FROM Geofence g WHERE g.tenantId = :tenantId AND g.id = :id AND g.deletedAt IS NULL")
    Optional<Geofence> findLiveById(TenantId tenantId, UUID id);
}