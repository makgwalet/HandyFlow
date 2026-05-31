package za.co.handyflow.platform.pos.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.pos.domain.model.PosPurchaseOrder;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;
import java.util.UUID;

public interface PosPurchaseOrderRepository extends JpaRepository<PosPurchaseOrder, UUID> {

    @Query("SELECT p FROM PosPurchaseOrder p WHERE p.tenantId = :tenantId ORDER BY p.createdAt DESC")
    Page<PosPurchaseOrder> findAll(TenantId tenantId, Pageable pageable);

    Optional<PosPurchaseOrder> findByIdAndTenantId(UUID id, TenantId tenantId);

    @Query("SELECT COALESCE(MAX(CAST(SUBSTRING(p.orderNumber, 4) AS int)), 0) FROM PosPurchaseOrder p WHERE p.tenantId = :tenantId")
    int findMaxOrderSequence(TenantId tenantId);
}
