package za.co.handyflow.platform.catalogue.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.catalogue.domain.model.CatalogueCategory;
import za.co.handyflow.platform.catalogue.domain.model.CatalogueItem;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CatalogueItemRepository extends JpaRepository<CatalogueItem, UUID>, JpaSpecificationExecutor<CatalogueItem> {

    /** Items that may be picked for new quotes, invoices and sales. */
    @Query("SELECT i FROM CatalogueItem i WHERE i.tenantId = :tenantId AND i.deletedAt IS NULL AND i.status = za.co.handyflow.platform.catalogue.domain.model.CatalogueItemStatus.ACTIVE "
            + "AND NOT EXISTS (SELECT 1 FROM CatalogueItem v WHERE v.parentId = i.id AND v.deletedAt IS NULL) ORDER BY i.name")
    List<CatalogueItem> findAllSellable(TenantId tenantId);

    /** Same, narrowed by what the user typed: matches name, SKU, barcode or brand. */
    @Query("SELECT i FROM CatalogueItem i WHERE i.tenantId = :tenantId AND i.deletedAt IS NULL AND i.status = za.co.handyflow.platform.catalogue.domain.model.CatalogueItemStatus.ACTIVE "
            + "AND (LOWER(i.name) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(i.sku) LIKE LOWER(CONCAT('%', :search, '%')) "
            + "OR LOWER(i.barcode) = LOWER(:search) OR LOWER(i.brand) LIKE LOWER(CONCAT('%', :search, '%'))) "
            + "AND NOT EXISTS (SELECT 1 FROM CatalogueItem v WHERE v.parentId = i.id AND v.deletedAt IS NULL) ORDER BY i.name")
    List<CatalogueItem> searchSellable(TenantId tenantId, String search);

    @Query("SELECT i FROM CatalogueItem i WHERE i.tenantId = :tenantId AND i.id = :id AND i.deletedAt IS NULL")
    Optional<CatalogueItem> findActiveById(TenantId tenantId, UUID id);

    @Query("SELECT i FROM CatalogueItem i WHERE i.tenantId = :tenantId AND i.category = :category AND i.deletedAt IS NULL")
    List<CatalogueItem> findAllByCategory(TenantId tenantId, CatalogueCategory category);

    @Query("SELECT COUNT(i) FROM CatalogueItem i WHERE i.tenantId = :tenantId AND i.category = :category AND i.deletedAt IS NULL")
    long countLiveInCategory(TenantId tenantId, CatalogueCategory category);

    /** Duplicate checks. Each ignores deleted items and, when updating, the item itself. */
    @Query("SELECT COUNT(i) > 0 FROM CatalogueItem i WHERE i.tenantId = :tenantId AND i.deletedAt IS NULL AND LOWER(i.name) = LOWER(:name) AND i.id <> :excludeId")
    boolean nameTaken(TenantId tenantId, String name, UUID excludeId);

    @Query("SELECT COUNT(i) > 0 FROM CatalogueItem i WHERE i.tenantId = :tenantId AND i.deletedAt IS NULL AND LOWER(i.sku) = LOWER(:sku) AND i.id <> :excludeId")
    boolean skuTaken(TenantId tenantId, String sku, UUID excludeId);

    @Query("SELECT COUNT(i) > 0 FROM CatalogueItem i WHERE i.tenantId = :tenantId AND i.deletedAt IS NULL AND LOWER(i.barcode) = LOWER(:barcode) AND i.id <> :excludeId")
    boolean barcodeTaken(TenantId tenantId, String barcode, UUID excludeId);

    /** POS terminal scan: only sellable items match. */
    @Query("SELECT i FROM CatalogueItem i WHERE i.tenantId = :tenantId AND LOWER(i.barcode) = LOWER(:barcode) AND i.deletedAt IS NULL AND i.status = za.co.handyflow.platform.catalogue.domain.model.CatalogueItemStatus.ACTIVE "
            + "AND NOT EXISTS (SELECT 1 FROM CatalogueItem v WHERE v.parentId = i.id AND v.deletedAt IS NULL)")
    Optional<CatalogueItem> findSellableByBarcode(TenantId tenantId, String barcode);

    // ── Variants ──
    @Query("SELECT i FROM CatalogueItem i WHERE i.tenantId = :tenantId AND i.parentId = :parentId AND i.deletedAt IS NULL ORDER BY i.variantLabel")
    List<CatalogueItem> findVariants(TenantId tenantId, UUID parentId);

    @Query("SELECT COUNT(i) FROM CatalogueItem i WHERE i.tenantId = :tenantId AND i.parentId = :parentId AND i.deletedAt IS NULL")
    long countVariants(TenantId tenantId, UUID parentId);

    /** Live variant counts for a page of items: rows of [parentId, count]. */
    @Query("SELECT i.parentId, COUNT(i) FROM CatalogueItem i WHERE i.tenantId = :tenantId AND i.deletedAt IS NULL AND i.parentId IN :parentIds GROUP BY i.parentId")
    List<Object[]> variantCounts(TenantId tenantId, java.util.Collection<UUID> parentIds);

    // ── Dashboard ──
    /** Top-level items by type and status: rows of [type, status, count]. */
    @Query("SELECT i.itemType, i.status, COUNT(i) FROM CatalogueItem i WHERE i.tenantId = :tenantId AND i.deletedAt IS NULL AND i.parentId IS NULL GROUP BY i.itemType, i.status")
    List<Object[]> countsByTypeAndStatus(TenantId tenantId);

    @Query("SELECT COUNT(i) FROM CatalogueItem i WHERE i.tenantId = :tenantId AND i.deletedAt IS NULL AND i.parentId IS NOT NULL AND i.status <> za.co.handyflow.platform.catalogue.domain.model.CatalogueItemStatus.ARCHIVED")
    long countLiveVariants(TenantId tenantId);

    /** Items that can be sold right now: active and not a parent whose variants are sold instead. */
    @Query("SELECT COUNT(i) FROM CatalogueItem i WHERE i.tenantId = :tenantId AND i.deletedAt IS NULL AND i.status = za.co.handyflow.platform.catalogue.domain.model.CatalogueItemStatus.ACTIVE "
            + "AND NOT EXISTS (SELECT 1 FROM CatalogueItem v WHERE v.parentId = i.id AND v.deletedAt IS NULL)")
    long countSellable(TenantId tenantId);

    /** Same checks as CompletenessRules: basic details missing on an item that is still in use. */
    @Query("SELECT COUNT(i) FROM CatalogueItem i WHERE i.tenantId = :tenantId AND i.deletedAt IS NULL "
            + "AND i.status NOT IN (za.co.handyflow.platform.catalogue.domain.model.CatalogueItemStatus.ARCHIVED, za.co.handyflow.platform.catalogue.domain.model.CatalogueItemStatus.DISCONTINUED) "
            + "AND NOT EXISTS (SELECT 1 FROM CatalogueItem v WHERE v.parentId = i.id AND v.deletedAt IS NULL) "
            + "AND (i.description IS NULL OR TRIM(i.description) = '' OR i.category IS NULL OR i.sku IS NULL OR i.defaultPrice <= 0 "
            + "OR (i.itemType IN (za.co.handyflow.platform.catalogue.domain.model.CatalogueItemType.PRODUCT, za.co.handyflow.platform.catalogue.domain.model.CatalogueItemType.CONSUMABLE) AND (i.barcode IS NULL OR i.brand IS NULL)))")
    long countIncomplete(TenantId tenantId);

    @Query("SELECT COUNT(i) FROM CatalogueItem i WHERE i.tenantId = :tenantId AND i.deletedAt IS NULL AND i.status = za.co.handyflow.platform.catalogue.domain.model.CatalogueItemStatus.DISCONTINUED AND i.statusChangedAt >= :since")
    long countDiscontinuedSince(TenantId tenantId, java.time.Instant since);

    /** Items per category (null id = no category), top-level and not archived: rows of [categoryId, count]. */
    @Query("SELECT c.id, COUNT(i) FROM CatalogueItem i LEFT JOIN i.category c WHERE i.tenantId = :tenantId AND i.deletedAt IS NULL "
            + "AND i.parentId IS NULL AND i.status <> za.co.handyflow.platform.catalogue.domain.model.CatalogueItemStatus.ARCHIVED GROUP BY c.id")
    List<Object[]> countsByCategory(TenantId tenantId);

    @Query("SELECT i.createdAt FROM CatalogueItem i WHERE i.tenantId = :tenantId AND i.deletedAt IS NULL AND i.createdAt >= :since")
    List<java.time.Instant> createdSince(TenantId tenantId, java.time.Instant since);

    @Query("SELECT COUNT(i) FROM CatalogueItem i WHERE i.tenantId = :tenantId AND i.deletedAt IS NULL AND i.createdAt < :before")
    long countCreatedBefore(TenantId tenantId, java.time.Instant before);

    @Query("SELECT i FROM CatalogueItem i WHERE i.tenantId = :tenantId AND i.deletedAt IS NULL ORDER BY i.updatedAt DESC")
    List<CatalogueItem> recentlyChanged(TenantId tenantId, org.springframework.data.domain.Pageable pageable);

    // ── Import, export, bulk, data quality ──
    /** Every live item with its category loaded, for building an import plan or an export. */
    @Query("SELECT i FROM CatalogueItem i LEFT JOIN FETCH i.category WHERE i.tenantId = :tenantId AND i.deletedAt IS NULL ORDER BY i.name")
    List<CatalogueItem> allLive(TenantId tenantId);

    @Query("SELECT DISTINCT i.parentId FROM CatalogueItem i WHERE i.tenantId = :tenantId AND i.deletedAt IS NULL AND i.parentId IS NOT NULL")
    List<UUID> parentIdsWithVariants(TenantId tenantId);

    @Query("SELECT i FROM CatalogueItem i LEFT JOIN FETCH i.category WHERE i.tenantId = :tenantId AND i.deletedAt IS NULL AND i.id IN :ids")
    List<CatalogueItem> findLiveByIds(TenantId tenantId, java.util.Collection<UUID> ids);
}
