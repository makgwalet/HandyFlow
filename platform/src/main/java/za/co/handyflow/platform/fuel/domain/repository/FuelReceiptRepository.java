// fuel/domain/repository/FuelReceiptRepository.java

package za.co.handyflow.platform.fuel.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fuel.domain.model.FuelReceipt;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FuelReceiptRepository extends JpaRepository<FuelReceipt, UUID> {

    /** Most recent receipts of a fuel type across the tenant's tanks, newest first. Used as the purchase-cost reference for pricing. */
    @Query("SELECT r FROM FuelReceipt r, FuelTank t WHERE r.tankId = t.id AND r.tenantId = :tenantId AND t.tenantId = :tenantId AND t.fuelType = :fuelType AND r.deletedAt IS NULL AND r.reversedAt IS NULL ORDER BY r.receivedAt DESC")
    List<FuelReceipt> latestForFuelType(TenantId tenantId, String fuelType, Pageable pageable);

    @Query("SELECT r FROM FuelReceipt r WHERE r.tenantId = :tenantId AND r.tankId = :tankId AND r.deletedAt IS NULL ORDER BY r.receivedAt DESC")
    Page<FuelReceipt> findByTank(TenantId tenantId, UUID tankId, Pageable pageable);

    @Query("SELECT r FROM FuelReceipt r WHERE r.tenantId = :tenantId AND r.deletedAt IS NULL ORDER BY r.receivedAt DESC")
    Page<FuelReceipt> findAllActive(TenantId tenantId, Pageable pageable);

    @Query("SELECT r FROM FuelReceipt r WHERE r.tenantId = :tenantId AND r.deletedAt IS NULL AND r.tankId IN :tankIds ORDER BY r.receivedAt DESC")
    Page<FuelReceipt> findActiveForTanks(TenantId tenantId, java.util.Collection<UUID> tankIds, Pageable pageable);

    /**
     * Most recent receipt for a tank — backs the reorder suggestion's "last supplier" hint.
     */
    @Query("SELECT r FROM FuelReceipt r WHERE r.tenantId = :tenantId AND r.tankId = :tankId AND r.deletedAt IS NULL AND r.reversedAt IS NULL ORDER BY r.receivedAt DESC LIMIT 1")
    Optional<FuelReceipt> findMostRecentForTank(TenantId tenantId, UUID tankId);

    /**
     * Backs the supplier statement report — full (non-paginated) receipts from one supplier within a date range.
     */
    @Query("SELECT r FROM FuelReceipt r WHERE r.tenantId = :tenantId AND r.supplierId = :supplierId AND r.deletedAt IS NULL AND r.reversedAt IS NULL AND r.receivedAt BETWEEN :from AND :to ORDER BY r.receivedAt ASC")
    List<FuelReceipt> findBySupplierAndReceivedAtBetween(TenantId tenantId, UUID supplierId, Instant from, Instant to);

    @Query("SELECT r FROM FuelReceipt r WHERE r.tenantId = :tenantId AND r.id = :id AND r.deletedAt IS NULL")
    Optional<FuelReceipt> findActiveById(TenantId tenantId, UUID id);

    /** Litres received against a purchase order, net of reversed receipts. */
    @Query("SELECT COALESCE(SUM(r.litresReceived), 0) FROM FuelReceipt r WHERE r.tenantId = :tenantId AND r.purchaseOrderId = :poId AND r.deletedAt IS NULL AND r.reversedAt IS NULL")
    java.math.BigDecimal sumReceivedForPo(TenantId tenantId, UUID poId);

    /** Same, for many orders at once: rows of [purchaseOrderId, litres]. */
    @Query("SELECT r.purchaseOrderId, SUM(r.litresReceived) FROM FuelReceipt r WHERE r.tenantId = :tenantId AND r.purchaseOrderId IS NOT NULL AND r.deletedAt IS NULL AND r.reversedAt IS NULL GROUP BY r.purchaseOrderId")
    List<Object[]> sumReceivedByPo(TenantId tenantId);

    @Query("SELECT r FROM FuelReceipt r WHERE r.tenantId = :tenantId AND r.purchaseOrderId = :poId AND r.deletedAt IS NULL ORDER BY r.receivedAt")
    List<FuelReceipt> findByPo(TenantId tenantId, UUID poId);

    @Query("SELECT r FROM FuelReceipt r WHERE r.tenantId = :tenantId AND r.id IN :ids AND r.deletedAt IS NULL")
    List<FuelReceipt> findActiveByIds(TenantId tenantId, java.util.Collection<UUID> ids);

    /** Receipts from a supplier that are not reversed and are not yet on any supplier invoice: what an invoice can still cover. */
    @Query("SELECT r FROM FuelReceipt r WHERE r.tenantId = :tenantId AND r.supplierId = :supplierId AND r.deletedAt IS NULL AND r.reversedAt IS NULL "
            + "AND r.id NOT IN (SELECT l.receiptId FROM FuelSupplierInvoiceReceipt l WHERE l.tenantId = :tenantId) ORDER BY r.receivedAt DESC")
    List<FuelReceipt> findInvoiceCandidates(TenantId tenantId, UUID supplierId, Pageable pageable);
}
