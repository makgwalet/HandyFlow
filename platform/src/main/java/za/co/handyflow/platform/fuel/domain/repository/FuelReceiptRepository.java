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

    @Query("SELECT r FROM FuelReceipt r WHERE r.tankId = :tankId AND r.deletedAt IS NULL ORDER BY r.receivedAt DESC")
    Page<FuelReceipt> findByTank(UUID tankId, Pageable pageable);

    @Query("SELECT r FROM FuelReceipt r WHERE r.tenantId = :tenantId AND r.deletedAt IS NULL ORDER BY r.receivedAt DESC")
    Page<FuelReceipt> findAllActive(TenantId tenantId, Pageable pageable);

    /**
     * Most recent receipt for a tank — backs the reorder suggestion's "last supplier" hint.
     */
    @Query("SELECT r FROM FuelReceipt r WHERE r.tankId = :tankId AND r.deletedAt IS NULL ORDER BY r.receivedAt DESC LIMIT 1")
    Optional<FuelReceipt> findMostRecentForTank(UUID tankId);

    /**
     * Backs the supplier statement report — full (non-paginated) receipts from one supplier within a date range.
     */
    @Query("SELECT r FROM FuelReceipt r WHERE r.tenantId = :tenantId AND r.supplierId = :supplierId AND r.deletedAt IS NULL AND r.receivedAt BETWEEN :from AND :to ORDER BY r.receivedAt ASC")
    List<FuelReceipt> findBySupplierAndReceivedAtBetween(TenantId tenantId, UUID supplierId, Instant from, Instant to);
}