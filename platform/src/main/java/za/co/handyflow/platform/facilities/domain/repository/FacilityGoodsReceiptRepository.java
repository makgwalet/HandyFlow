package za.co.handyflow.platform.facilities.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.facilities.domain.model.FacilityGoodsReceipt;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

public interface FacilityGoodsReceiptRepository extends JpaRepository<FacilityGoodsReceipt, UUID> {

    @Query("SELECT r FROM FacilityGoodsReceipt r WHERE r.tenantId.value = :#{#tenantId.value} " +
            "AND r.purchaseOrderId = :purchaseOrderId ORDER BY r.createdAt ASC")
    List<FacilityGoodsReceipt> findForPurchaseOrder(@Param("tenantId") TenantId tenantId, @Param("purchaseOrderId") UUID purchaseOrderId);
}
