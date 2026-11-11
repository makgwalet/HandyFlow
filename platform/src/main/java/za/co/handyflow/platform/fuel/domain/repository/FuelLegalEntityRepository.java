package za.co.handyflow.platform.fuel.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fuel.domain.model.FuelLegalEntity;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FuelLegalEntityRepository extends JpaRepository<FuelLegalEntity, UUID> {
    @Query("SELECT e FROM FuelLegalEntity e WHERE e.tenantId = :tenantId AND e.deletedAt IS NULL ORDER BY e.name")
    List<FuelLegalEntity> findAllActive(TenantId tenantId);

    @Query("SELECT e FROM FuelLegalEntity e WHERE e.tenantId = :tenantId AND e.id = :id AND e.deletedAt IS NULL")
    Optional<FuelLegalEntity> findActiveById(TenantId tenantId, UUID id);

    @Query("SELECT COUNT(e) > 0 FROM FuelLegalEntity e WHERE e.tenantId = :tenantId AND LOWER(e.name) = LOWER(:name) AND e.deletedAt IS NULL AND (:excludeId IS NULL OR e.id <> :excludeId)")
    boolean nameTaken(TenantId tenantId, String name, UUID excludeId);
}
