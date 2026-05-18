package za.co.handyflow.platform.catalogue.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.catalogue.domain.model.CatalogueItem;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CatalogueItemRepository extends JpaRepository<CatalogueItem, UUID> {

    // WHY deleted_at IS NULL? Soft delete — we never return deleted items
    @Query("SELECT i FROM CatalogueItem i WHERE i.tenantId = :tenantId AND i.deletedAt IS NULL ORDER BY i.name")
    List<CatalogueItem> findAllActive(TenantId tenantId);

    @Query("SELECT i FROM CatalogueItem i WHERE i.tenantId = :tenantId AND i.deletedAt IS NULL AND LOWER(i.name) LIKE LOWER(CONCAT('%', :search, '%')) ORDER BY i.name")
    List<CatalogueItem> searchByName(TenantId tenantId, String search);

    @Query("SELECT i FROM CatalogueItem i WHERE i.tenantId = :tenantId AND i.id = :id AND i.deletedAt IS NULL")
    Optional<CatalogueItem> findActiveById(TenantId tenantId, UUID id);

    boolean existsByTenantIdAndNameAndDeletedAtIsNull(TenantId tenantId, String name);
}