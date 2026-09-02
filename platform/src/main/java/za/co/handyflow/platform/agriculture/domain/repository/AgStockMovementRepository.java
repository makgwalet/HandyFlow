package za.co.handyflow.platform.agriculture.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.agriculture.domain.model.AgStockMovement;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public interface AgStockMovementRepository extends JpaRepository<AgStockMovement, UUID> {

    @Query("SELECT m FROM AgStockMovement m WHERE m.tenantId = :tenantId AND m.id = :id")
    Optional<AgStockMovement> findByTenantAndId(TenantId tenantId, UUID id);

    @Query("SELECT m FROM AgStockMovement m WHERE m.tenantId = :tenantId AND m.inventoryItemId = :inventoryItemId ORDER BY m.movementDate DESC")
    Page<AgStockMovement> findByInventoryItem(TenantId tenantId, UUID inventoryItemId, Pageable pageable);

    // Backs AgCostReportingService's seed-cost figure — AgCropCycle has no
    // seedCost field of its own (seed quantity only), so seed cost is
    // recovered from the ISSUE movement AgCropCycleService.issueSeed()
    // creates against the seed AgInventoryItem, tagged with
    // referenceType="AgCropCycle"/referenceId=<cycle id>. COALESCE so a
    // cycle with no seed movement (no seed tracked, or seed cost unknown at
    // issue time) returns 0, not null.
    @Query("SELECT COALESCE(SUM(m.totalCost), 0) FROM AgStockMovement m WHERE m.tenantId = :tenantId AND m.referenceType = :referenceType AND m.referenceId = :referenceId")
    BigDecimal sumTotalCostByReference(TenantId tenantId, String referenceType, UUID referenceId);
}
