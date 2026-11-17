package za.co.handyflow.platform.supplychain.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.supplychain.domain.model.ScGrLine;

import java.util.List;
import java.util.UUID;

public interface ScGrLineRepository extends JpaRepository<ScGrLine, UUID> {

    @Query("SELECT l FROM ScGrLine l WHERE l.goodsReceiptId = :grId ORDER BY l.id")
    List<ScGrLine> findByGoodsReceiptId(@Param("grId") UUID goodsReceiptId);

    @Query("SELECT l FROM ScGrLine l WHERE l.poLineId = :poLineId")
    List<ScGrLine> findByPoLineId(@Param("poLineId") UUID poLineId);

    /**
     * NEW: accepted quantity per PO line across all POSTED goods receipts for the PO.
     * Returns [poLineId, SUM(qtyReceived - qtyRejected)]. This is what invoice
     * matching compares against — actual accepted quantity, not merely "a GR is POSTED".
     */
    @Query("""
            SELECT l.poLineId, SUM(l.qtyReceived - l.qtyRejected)
            FROM ScGrLine l, ScGoodsReceipt g
            WHERE g.id = l.goodsReceiptId
              AND g.tenantId = :tenantId AND g.purchaseOrderId = :poId
              AND g.status = 'POSTED' AND l.poLineId IS NOT NULL
            GROUP BY l.poLineId
            """)
    List<Object[]> sumAcceptedByPoLine(@Param("tenantId") UUID tenantId, @Param("poId") UUID poId);

    /** NEW (chunk 3): PO lines that have goods receipt lines (any non-reversed GR) — can't be removed. */
    @Query("""
            SELECT DISTINCT l.poLineId
            FROM ScGrLine l, ScGoodsReceipt g
            WHERE g.id = l.goodsReceiptId
              AND g.tenantId = :tenantId AND g.purchaseOrderId = :poId
              AND g.status <> :excludedStatus AND l.poLineId IS NOT NULL
            """)
    List<UUID> findReceiptedPoLineIds(@Param("tenantId") UUID tenantId,
                                      @Param("poId") UUID poId,
                                      @Param("excludedStatus") String excludedStatus);
}
