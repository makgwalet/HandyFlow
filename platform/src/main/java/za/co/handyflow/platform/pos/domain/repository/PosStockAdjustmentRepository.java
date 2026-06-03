package za.co.handyflow.platform.pos.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.pos.domain.model.PosStockAdjustment;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;
import java.util.UUID;

public interface PosStockAdjustmentRepository extends JpaRepository<PosStockAdjustment, UUID> {

    @Query("SELECT a FROM PosStockAdjustment a WHERE a.tenantId = :tenantId ORDER BY a.createdAt DESC")
    Page<PosStockAdjustment> findAll(TenantId tenantId, Pageable pageable);

    Optional<PosStockAdjustment> findByIdAndTenantId(UUID id, TenantId tenantId);

    @Query("SELECT COALESCE(MAX(CAST(SUBSTRING(a.adjustmentNumber, 4) AS int)), 0) FROM PosStockAdjustment a WHERE a.tenantId = :tenantId")
    int findMaxAdjustmentSequence(TenantId tenantId);
}
