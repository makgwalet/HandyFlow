package za.co.handyflow.platform.agriculture.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.agriculture.domain.model.AgCropCycle;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;
import java.util.UUID;

/**
 * Farm-scoped, mirroring AgAnimalRepository/AgGroupRepository's own shape —
 * AgCropCycle plays the same "central tracking unit" role for Crops that
 * AgGroup plays for Livestock. Ordered by createdAt DESC rather than a
 * name/tag field, since cycleName/variety are both optional and there is
 * no natural alphabetic sort key on this entity.
 */
public interface AgCropCycleRepository extends JpaRepository<AgCropCycle, UUID> {

    @Query("SELECT c FROM AgCropCycle c WHERE c.tenantId = :tenantId AND c.id = :id AND c.deletedAt IS NULL")
    Optional<AgCropCycle> findActiveById(TenantId tenantId, UUID id);

    @Query("SELECT c FROM AgCropCycle c WHERE c.tenantId = :tenantId AND c.farmId = :farmId AND c.deletedAt IS NULL ORDER BY c.createdAt DESC")
    Page<AgCropCycle> findAllActiveForFarm(TenantId tenantId, UUID farmId, Pageable pageable);

    @Query("SELECT c FROM AgCropCycle c WHERE c.tenantId = :tenantId AND c.farmId = :farmId AND c.status = :status AND c.deletedAt IS NULL ORDER BY c.createdAt DESC")
    Page<AgCropCycle> findByStatusForFarm(TenantId tenantId, UUID farmId, String status, Pageable pageable);

    @Query("SELECT c FROM AgCropCycle c WHERE c.tenantId = :tenantId AND c.seasonId = :seasonId AND c.deletedAt IS NULL ORDER BY c.createdAt DESC")
    Page<AgCropCycle> findAllActiveForSeason(TenantId tenantId, UUID seasonId, Pageable pageable);
}
