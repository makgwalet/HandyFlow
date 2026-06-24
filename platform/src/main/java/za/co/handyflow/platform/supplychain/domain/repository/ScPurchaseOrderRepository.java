package za.co.handyflow.platform.supplychain.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.supplychain.domain.model.ScPurchaseOrder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScPurchaseOrderRepository extends JpaRepository<ScPurchaseOrder, UUID> {

    @Query("SELECT p FROM ScPurchaseOrder p WHERE p.tenantId = :tenantId ORDER BY p.createdAt DESC")
    Page<ScPurchaseOrder> findByTenantId(@Param("tenantId") UUID tenantId, Pageable pageable);

    @Query("SELECT p FROM ScPurchaseOrder p WHERE p.tenantId = :tenantId AND p.status = :status ORDER BY p.createdAt DESC")
    Page<ScPurchaseOrder> findByTenantIdAndStatus(@Param("tenantId") UUID tenantId,
                                                  @Param("status") String status, Pageable pageable);

    @Query("SELECT p FROM ScPurchaseOrder p WHERE p.tenantId = :tenantId AND p.id = :id")
    Optional<ScPurchaseOrder> findByTenantIdAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    @Query("SELECT COALESCE(MAX(CAST(SUBSTRING(p.orderNumber, 4) AS int)), 0) FROM ScPurchaseOrder p WHERE p.tenantId = :tenantId")
    int findMaxOrderSequence(@Param("tenantId") UUID tenantId);

    @Query("SELECT COUNT(p) FROM ScPurchaseOrder p WHERE p.tenantId = :tenantId AND p.status IN :statuses")
    long countByTenantIdAndStatusIn(@Param("tenantId") UUID tenantId, @Param("statuses") List<String> statuses);
}
