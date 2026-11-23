package za.co.handyflow.platform.agriculture.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.agriculture.domain.model.AgInventoryItem;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AgInventoryItemRepository extends JpaRepository<AgInventoryItem, UUID> {

    @Query("SELECT i FROM AgInventoryItem i WHERE i.tenantId = :tenantId AND i.id = :id AND i.deletedAt IS NULL")
    Optional<AgInventoryItem> findActiveById(TenantId tenantId, UUID id);

    @Query("SELECT i FROM AgInventoryItem i WHERE i.tenantId = :tenantId AND i.farmId = :farmId AND i.deletedAt IS NULL ORDER BY i.itemName, i.id")
    Page<AgInventoryItem> findAllActiveForFarm(TenantId tenantId, UUID farmId, Pageable pageable);

    // Cross-tenant sweep for AgNotificationScheduler's daily 09:30 run — every
    // active item that has dropped below its reorder level, regardless of
    // tenant. No acknowledgement flag needed (see AgNotificationScheduler's
    // own Javadoc): crossing back above the level self-corrects the alert.
    // Only for farms that are still live, and not told about in the last REPEAT_DAYS (or since it was last restocked).
    @Query("SELECT i FROM AgInventoryItem i WHERE i.deletedAt IS NULL AND i.status = 'ACTIVE' " +
            "AND i.reorderLevel IS NOT NULL AND i.currentQuantity < i.reorderLevel " +
            "AND (i.lowStockNotifiedOn IS NULL OR i.lowStockNotifiedOn <= :repeatBefore) " +
            "AND i.farmId IN (SELECT f.id FROM AgFarm f WHERE f.deletedAt IS NULL AND f.status = 'ACTIVE')")
    List<AgInventoryItem> findBelowReorderLevelAcrossTenants(java.time.LocalDate repeatBefore);

    // FIX (Agriculture GAP 5 — mobile gap report, "attention" endpoint):
    // farm-scoped equivalent of the sweep above. AgInventoryItem already
    // has its own farmId, so this needed no join, unlike the health-event
    // and scouting-record equivalents.
    @Query("SELECT i FROM AgInventoryItem i WHERE i.tenantId = :tenantId AND i.farmId = :farmId " +
            "AND i.deletedAt IS NULL AND i.status = 'ACTIVE' " +
            "AND i.reorderLevel IS NOT NULL AND i.currentQuantity < i.reorderLevel " +
            "ORDER BY i.itemName")
    List<AgInventoryItem> findBelowReorderLevelForFarm(TenantId tenantId, UUID farmId);

    /** Stock items of the farm with something on hand; backs the "can this farm be deactivated or deleted" check. */
    @Query("SELECT COUNT(i) FROM AgInventoryItem i WHERE i.tenantId = :tenantId AND i.farmId = :farmId AND i.deletedAt IS NULL AND i.currentQuantity > 0")
    long countWithStockForFarm(TenantId tenantId, UUID farmId);

    // Names of the farm's active feed stock. Feeds the feed-type suggestions.
    @Query("SELECT i.itemName FROM AgInventoryItem i WHERE i.tenantId = :tenantId AND i.farmId = :farmId AND i.deletedAt IS NULL AND i.category = 'FEED' AND i.status = 'ACTIVE'")
    List<String> findActiveFeedNamesForFarm(TenantId tenantId, UUID farmId);
}
