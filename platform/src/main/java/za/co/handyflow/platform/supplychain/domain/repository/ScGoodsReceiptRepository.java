package za.co.handyflow.platform.supplychain.domain.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.supplychain.domain.enums.InvoiceStatus;
import za.co.handyflow.platform.supplychain.domain.model.ScGoodsReceipt;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScGoodsReceiptRepository extends JpaRepository<ScGoodsReceipt, UUID> {

    @Query("SELECT g FROM ScGoodsReceipt g WHERE g.tenantId = :tenantId ORDER BY g.createdAt DESC")
    Page<ScGoodsReceipt> findByTenantId(@Param("tenantId") UUID tenantId, Pageable pageable);

    @Query("SELECT g FROM ScGoodsReceipt g WHERE g.tenantId = :tenantId AND g.id = :id")
    Optional<ScGoodsReceipt> findByTenantIdAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    /**
     * NEW: row lock on the GR. Post and reverse take this FIRST, so two concurrent posts of
     * the same receipt are serialised and the loser re-reads status = POSTED after the
     * winner commits (a plain read-then-check would let both through and double the stock).
     * Lock order everywhere: goods receipt, then purchase order.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT g FROM ScGoodsReceipt g WHERE g.tenantId = :tenantId AND g.id = :id")
    Optional<ScGoodsReceipt> lockByTenantIdAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    @Query("SELECT g FROM ScGoodsReceipt g WHERE g.tenantId = :tenantId AND g.purchaseOrderId = :purchaseOrderId ORDER BY g.createdAt DESC")
    List<ScGoodsReceipt> findByTenantIdAndPurchaseOrderId(@Param("tenantId") UUID tenantId,
                                                          @Param("purchaseOrderId") UUID purchaseOrderId);

    /** NEW: non-cancelled supplier invoices that explicitly link this GR — a GR they rely on can't be reversed. */
    @Query("""
            SELECT COUNT(i) FROM ScSupplierInvoice i
            WHERE i.tenantId = :tenantId AND i.goodsReceiptId = :grId AND i.status <> :excludedStatus
            """)
    long countInvoicesLinkedToReceipt(@Param("tenantId") UUID tenantId,
                                      @Param("grId") UUID grId,
                                      @Param("excludedStatus") InvoiceStatus excludedStatus);
}
