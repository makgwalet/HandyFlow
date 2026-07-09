package za.co.handyflow.platform.pos.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.pos.domain.model.PosStockItem;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PosStockItemRepository extends JpaRepository<PosStockItem, UUID> {

    @Query("SELECT s FROM PosStockItem s WHERE s.tenantId = :tenantId ORDER BY s.createdAt DESC")
    Page<PosStockItem> findAll(TenantId tenantId, Pageable pageable);

    Optional<PosStockItem> findByTenantIdAndCatalogueItemId(TenantId tenantId, UUID catalogueItemId);

    Optional<PosStockItem> findByIdAndTenantId(UUID id, TenantId tenantId);

    // Low stock items — qty_on_hand <= reorder_level
    @Query("""
        SELECT s FROM PosStockItem s
        WHERE s.tenantId = :tenantId
        AND s.trackStock = true
        AND s.qtyOnHand <= s.reorderLevel
        ORDER BY s.qtyOnHand ASC
        """)
    List<PosStockItem> findLowStock(TenantId tenantId);

    @Query("SELECT COUNT(s) FROM PosStockItem s WHERE s.tenantId = :tenantId AND s.trackStock = true AND s.qtyOnHand <= s.reorderLevel")
    long countLowStock(TenantId tenantId);

    // NEW: backs the fix to PosService.getSummary(), which was previously
    // calling the raw JpaRepository.count() — no tenant filter at all,
    // returning a count of stock items across every tenant in the database
    // rather than just the current one. Every other line in that method
    // already scopes by tenantId; this one didn't.
    @Query("SELECT COUNT(s) FROM PosStockItem s WHERE s.tenantId = :tenantId")
    long countByTenantId(TenantId tenantId);
}