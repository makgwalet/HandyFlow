package za.co.handyflow.platform.catalogue.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.catalogue.domain.model.CatalogueUnit;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CatalogueUnitRepository extends JpaRepository<CatalogueUnit, UUID> {

    @Query("SELECT u FROM CatalogueUnit u WHERE u.tenantId = :tenantId AND u.deletedAt IS NULL ORDER BY u.code")
    List<CatalogueUnit> findAllLive(TenantId tenantId);

    @Query("SELECT u FROM CatalogueUnit u WHERE u.tenantId = :tenantId AND u.id = :id AND u.deletedAt IS NULL")
    Optional<CatalogueUnit> findLiveById(TenantId tenantId, UUID id);

    @Query("SELECT u FROM CatalogueUnit u WHERE u.tenantId = :tenantId AND u.deletedAt IS NULL AND LOWER(u.code) = LOWER(:code)")
    Optional<CatalogueUnit> findLiveByCode(TenantId tenantId, String code);

    @Query("SELECT COUNT(u) FROM CatalogueUnit u WHERE u.tenantId = :tenantId AND u.deletedAt IS NULL")
    long countLive(TenantId tenantId);

    /** How many live items carry this unit code (any status). */
    @Query("SELECT COUNT(i) FROM CatalogueItem i WHERE i.tenantId = :tenantId AND i.deletedAt IS NULL AND LOWER(i.unit) = LOWER(:code)")
    long countItemsUsing(TenantId tenantId, String code);
}
