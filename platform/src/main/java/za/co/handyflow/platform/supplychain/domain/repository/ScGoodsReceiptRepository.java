package za.co.handyflow.platform.supplychain.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.supplychain.domain.model.ScGoodsReceipt;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScGoodsReceiptRepository extends JpaRepository<ScGoodsReceipt, UUID> {

    @Query("SELECT g FROM ScGoodsReceipt g WHERE g.tenantId = :tenantId ORDER BY g.createdAt DESC")
    Page<ScGoodsReceipt> findByTenantId(@Param("tenantId") UUID tenantId, Pageable pageable);

    @Query("SELECT g FROM ScGoodsReceipt g WHERE g.tenantId = :tenantId AND g.id = :id")
    Optional<ScGoodsReceipt> findByTenantIdAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    @Query("SELECT g FROM ScGoodsReceipt g WHERE g.tenantId = :tenantId AND g.purchaseOrderId = :purchaseOrderId ORDER BY g.createdAt DESC")
    List<ScGoodsReceipt> findByTenantIdAndPurchaseOrderId(@Param("tenantId") UUID tenantId,
                                                          @Param("purchaseOrderId") UUID purchaseOrderId);
}