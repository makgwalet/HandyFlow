package za.co.handyflow.platform.agriculture.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.agriculture.domain.model.AgCropType;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;
import java.util.UUID;

/**
 * Mirrors AgSpeciesRepository exactly — AgCropType is the Crops
 * sub-domain's direct structural counterpart to AgSpecies (tenant-wide
 * catalogue, not farm-scoped).
 */
public interface AgCropTypeRepository extends JpaRepository<AgCropType, UUID> {

    @Query("SELECT c FROM AgCropType c WHERE c.tenantId = :tenantId AND c.id = :id AND c.deletedAt IS NULL")
    Optional<AgCropType> findActiveById(TenantId tenantId, UUID id);

    @Query("SELECT c FROM AgCropType c WHERE c.tenantId = :tenantId AND c.deletedAt IS NULL ORDER BY c.name")
    Page<AgCropType> findAllActive(TenantId tenantId, Pageable pageable);

    @Query("SELECT c FROM AgCropType c WHERE c.tenantId = :tenantId AND c.category = :category AND c.deletedAt IS NULL ORDER BY c.name")
    Page<AgCropType> findAllActiveByCategory(TenantId tenantId, String category, Pageable pageable);
}
