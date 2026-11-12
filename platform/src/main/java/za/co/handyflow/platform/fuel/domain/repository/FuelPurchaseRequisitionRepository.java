package za.co.handyflow.platform.fuel.domain.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fuel.domain.model.FuelPurchaseRequisition;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FuelPurchaseRequisitionRepository extends JpaRepository<FuelPurchaseRequisition, UUID> {

    @Query("SELECT r FROM FuelPurchaseRequisition r WHERE r.tenantId = :tenantId ORDER BY r.createdAt DESC")
    List<FuelPurchaseRequisition> findAllForTenant(TenantId tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM FuelPurchaseRequisition r WHERE r.tenantId = :tenantId AND r.id = :id")
    Optional<FuelPurchaseRequisition> findByIdForUpdate(TenantId tenantId, UUID id);

    /** Requisitions for this tank that are still moving: a new automatic draft would double up. */
    @Query("SELECT COUNT(r) FROM FuelPurchaseRequisition r WHERE r.tenantId = :tenantId AND r.tankId = :tankId AND r.status IN ('DRAFT','PENDING_APPROVAL','APPROVED')")
    long countOpenForTank(TenantId tenantId, UUID tankId);

    /** Requisitions for this tank already turned into an order that has not been fully received yet. */
    @Query("SELECT COUNT(r) FROM FuelPurchaseRequisition r, FuelPurchaseOrder p WHERE r.purchaseOrderId = p.id AND r.tenantId = :tenantId AND p.tenantId = :tenantId "
            + "AND r.tankId = :tankId AND r.status = 'CONVERTED' AND p.deletedAt IS NULL AND p.status IN ('DRAFT','PENDING_APPROVAL','ORDERED','PART_RECEIVED')")
    long countOnOrderForTank(TenantId tenantId, UUID tankId);
}
