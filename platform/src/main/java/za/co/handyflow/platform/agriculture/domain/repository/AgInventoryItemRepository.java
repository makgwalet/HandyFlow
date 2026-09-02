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

    @Query("SELECT i FROM AgInventoryItem i WHERE i.tenantId = :tenantId AND i.farmId = :farmId AND i.deletedAt IS NULL ORDER BY i.itemName")
    Page<AgInventoryItem> findAllActiveForFarm(TenantId tenantId, UUID farmId, Pageable pageable);

    // Cross-tenant sweep for AgNotificationScheduler's daily 09:30 run — every
    // active item that has dropped below its reorder level, regardless of
    // tenant. No acknowledgement flag needed (see AgNotificationScheduler's
    // own Javadoc): crossing back above the level self-corrects the alert.
    @Query("SELECT i FROM AgInventoryItem i WHERE i.deletedAt IS NULL AND i.status = 'ACTIVE' " +
            "AND i.reorderLevel IS NOT NULL AND i.currentQuantity < i.reorderLevel")
    List<AgInventoryItem> findBelowReorderLevelAcrossTenants();
}
