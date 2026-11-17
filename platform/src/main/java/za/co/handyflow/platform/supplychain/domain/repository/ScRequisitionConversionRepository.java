package za.co.handyflow.platform.supplychain.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.supplychain.domain.enums.PoStatus;
import za.co.handyflow.platform.supplychain.domain.model.ScRequisitionConversion;

import java.util.List;
import java.util.UUID;

public interface ScRequisitionConversionRepository extends JpaRepository<ScRequisitionConversion, UUID> {

    @Query("SELECT c FROM ScRequisitionConversion c WHERE c.tenantId = :tenantId AND c.requisitionId = :requisitionId ORDER BY c.createdAt")
    List<ScRequisitionConversion> findByRequisition(@Param("tenantId") UUID tenantId, @Param("requisitionId") UUID requisitionId);

    /**
     * Quantity already ordered per requisition line — counting only conversions whose PO is NOT
     * cancelled, so cancelling a PO releases the quantity back to the requisition.
     * Returns [requisitionLineId, SUM(qty)].
     */
    @Query("""
            SELECT c.requisitionLineId, SUM(c.qty)
            FROM ScRequisitionConversion c, ScPurchaseOrder po
            WHERE po.id = c.purchaseOrderId AND po.status <> :cancelled
              AND c.tenantId = :tenantId AND c.requisitionId = :requisitionId
            GROUP BY c.requisitionLineId
            """)
    List<Object[]> sumActiveByLine(@Param("tenantId") UUID tenantId,
                                   @Param("requisitionId") UUID requisitionId,
                                   @Param("cancelled") PoStatus cancelled);
}
