package za.co.handyflow.platform.catalogue.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.catalogue.domain.model.CatalogueItemMedia;
import za.co.handyflow.platform.catalogue.domain.model.MediaRules;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CatalogueItemMediaRepository extends JpaRepository<CatalogueItemMedia, UUID> {

    @Query("SELECT m FROM CatalogueItemMedia m WHERE m.tenantId = :tenantId AND m.itemId = :itemId AND m.deletedAt IS NULL ORDER BY m.kind, m.sortOrder, m.uploadedAt")
    List<CatalogueItemMedia> forItem(TenantId tenantId, UUID itemId);

    @Query("SELECT m FROM CatalogueItemMedia m WHERE m.tenantId = :tenantId AND m.itemId = :itemId AND m.id = :id AND m.deletedAt IS NULL")
    Optional<CatalogueItemMedia> findInItem(TenantId tenantId, UUID itemId, UUID id);

    @Query("SELECT COUNT(m) FROM CatalogueItemMedia m WHERE m.tenantId = :tenantId AND m.itemId = :itemId AND m.kind = :kind AND m.deletedAt IS NULL")
    long count(TenantId tenantId, UUID itemId, MediaRules.Kind kind);

    @Query("SELECT m FROM CatalogueItemMedia m WHERE m.tenantId = :tenantId AND m.itemId = :itemId AND m.primary = true AND m.deletedAt IS NULL")
    Optional<CatalogueItemMedia> primaryOf(TenantId tenantId, UUID itemId);

    /** Ids of items that have at least one file of this kind; for the data-quality and report checks. */
    @Query("SELECT DISTINCT m.itemId FROM CatalogueItemMedia m WHERE m.tenantId = :tenantId AND m.kind = :kind AND m.deletedAt IS NULL")
    List<UUID> itemIdsWithMedia(TenantId tenantId, MediaRules.Kind kind);
}
