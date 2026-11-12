package za.co.handyflow.platform.fuel.domain.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fuel.domain.model.FuelPurchaseOrder;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FuelPurchaseOrderRepository extends JpaRepository<FuelPurchaseOrder, UUID> {

    @Query("SELECT p FROM FuelPurchaseOrder p WHERE p.tenantId = :tenantId AND p.deletedAt IS NULL ORDER BY p.createdAt DESC")
    List<FuelPurchaseOrder> findAllActive(TenantId tenantId);

    @Query("SELECT p FROM FuelPurchaseOrder p WHERE p.tenantId = :tenantId AND p.id = :id AND p.deletedAt IS NULL")
    Optional<FuelPurchaseOrder> findActiveById(TenantId tenantId, UUID id);

    /** Locked so two receipts arriving together cannot both squeeze under the tolerance. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM FuelPurchaseOrder p WHERE p.tenantId = :tenantId AND p.id = :id AND p.deletedAt IS NULL")
    Optional<FuelPurchaseOrder> findActiveByIdForUpdate(TenantId tenantId, UUID id);

    @Query("SELECT COUNT(p) FROM FuelPurchaseOrder p WHERE p.tenantId = :tenantId AND p.deletedAt IS NULL AND p.status IN ('ORDERED','PART_RECEIVED')")
    long countAwaitingDelivery(TenantId tenantId);

    /** Orders placed but not fully received, past their expected date, not yet alerted. */
    @Query("SELECT p FROM FuelPurchaseOrder p WHERE p.tenantId = :tenantId AND p.deletedAt IS NULL AND p.status IN ('ORDERED','PART_RECEIVED') "
            + "AND p.expectedDate IS NOT NULL AND p.expectedDate < :today AND p.overdueAlertedAt IS NULL ORDER BY p.expectedDate")
    List<FuelPurchaseOrder> findOverdueNotAlerted(TenantId tenantId, java.time.LocalDate today);
}
