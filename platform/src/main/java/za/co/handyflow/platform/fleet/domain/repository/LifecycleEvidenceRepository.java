package za.co.handyflow.platform.fleet.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fleet.domain.model.LifecycleEvidence;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LifecycleEvidenceRepository extends JpaRepository<LifecycleEvidence, UUID> {

    @Query("SELECT e FROM LifecycleEvidence e WHERE e.tenantId = :tenantId AND e.vehicleId = :vehicleId " +
            "AND e.deletedAt IS NULL ORDER BY e.uploadedAt")
    List<LifecycleEvidence> findActiveByVehicle(TenantId tenantId, UUID vehicleId);

    @Query("SELECT e FROM LifecycleEvidence e WHERE e.tenantId = :tenantId AND e.vehicleId = :vehicleId " +
            "AND e.eventId = :eventId AND e.id = :id AND e.deletedAt IS NULL")
    Optional<LifecycleEvidence> findActiveById(TenantId tenantId, UUID vehicleId, UUID eventId, UUID id);

    @Query("SELECT COUNT(e) FROM LifecycleEvidence e WHERE e.tenantId = :tenantId AND e.eventId = :eventId " +
            "AND e.deletedAt IS NULL")
    long countActiveByEvent(TenantId tenantId, UUID eventId);
}