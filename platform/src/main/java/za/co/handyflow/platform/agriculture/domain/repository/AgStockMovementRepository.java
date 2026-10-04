package za.co.handyflow.platform.agriculture.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.agriculture.domain.model.AgStockMovement;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.util.Optional;
import java.time.LocalDate;
import java.util.List;
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

    // Trends: seed cost is the stock issued against a crop cycle (the cost reports' definition): [movementDate, totalCost, cropCycleId].
    @Query("SELECT m.movementDate, m.totalCost, m.referenceId FROM AgStockMovement m WHERE m.tenantId = :tenantId AND m.referenceType = 'AgCropCycle' AND m.movementDate >= :startDate AND m.movementDate <= :endDate AND m.totalCost IS NOT NULL")
    List<Object[]> findCropCycleCostsBetween(TenantId tenantId, LocalDate startDate, LocalDate endDate);

    // ADR-001 W7: what a farm's stock RECEIPTS cost, by supplier, in a date range. Rows are [supplierId (null = none recorded), receipts, spend, receiptsWithNoCost].
    // The spend is each receipt's own totalCost (quantity x unit cost when it was received), never recomputed from today's price.
    @Query("""
        SELECT m.supplierId, COUNT(m), COALESCE(SUM(m.totalCost), 0), SUM(CASE WHEN m.totalCost IS NULL THEN 1 ELSE 0 END)
        FROM AgStockMovement m, AgInventoryItem i
        WHERE m.tenantId = :tenantId AND i.tenantId = :tenantId AND m.inventoryItemId = i.id AND i.farmId = :farmId
        AND m.movementType = 'RECEIPT' AND m.movementDate >= :from AND m.movementDate <= :to
        GROUP BY m.supplierId
        """)
    List<Object[]> receiptSpendBySupplier(TenantId tenantId, UUID farmId, LocalDate from, LocalDate to);
}
