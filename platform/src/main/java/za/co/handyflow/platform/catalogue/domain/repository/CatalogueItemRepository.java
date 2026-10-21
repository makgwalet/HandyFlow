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
    @Query("SELECT i FROM CatalogueItem i WHERE i.tenantId = :tenantId AND i.deletedAt IS NULL AND i.status = za.co.handyflow.platform.catalogue.domain.model.CatalogueItemStatus.ACTIVE ORDER BY i.name")
    List<CatalogueItem> findAllSellable(TenantId tenantId);

    /** Same, narrowed by what the user typed: matches name, SKU, barcode or brand. */
    @Query("SELECT i FROM CatalogueItem i WHERE i.tenantId = :tenantId AND i.deletedAt IS NULL AND i.status = za.co.handyflow.platform.catalogue.domain.model.CatalogueItemStatus.ACTIVE "
            + "AND (LOWER(i.name) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(i.sku) LIKE LOWER(CONCAT('%', :search, '%')) "
            + "OR LOWER(i.barcode) = LOWER(:search) OR LOWER(i.brand) LIKE LOWER(CONCAT('%', :search, '%'))) ORDER BY i.name")
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
    @Query("SELECT i FROM CatalogueItem i WHERE i.tenantId = :tenantId AND LOWER(i.barcode) = LOWER(:barcode) AND i.deletedAt IS NULL AND i.status = za.co.handyflow.platform.catalogue.domain.model.CatalogueItemStatus.ACTIVE")
    Optional<CatalogueItem> findSellableByBarcode(TenantId tenantId, String barcode);
}
