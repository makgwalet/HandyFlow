package za.co.handyflow.platform.facilities.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.facilities.domain.model.FacilityStockMovement;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

public interface FacilityStockMovementRepository extends JpaRepository<FacilityStockMovement, UUID> {

    @Query("SELECT m FROM FacilityStockMovement m WHERE m.tenantId.value = :#{#tenantId.value} AND m.itemId = :itemId "
            + "ORDER BY m.createdAt DESC")
    Page<FacilityStockMovement> findForItem(@Param("tenantId") TenantId tenantId, @Param("itemId") UUID itemId, Pageable pageable);

    @Query("SELECT m FROM FacilityStockMovement m WHERE m.tenantId.value = :#{#tenantId.value} AND m.workOrderId = :workOrderId "
            + "ORDER BY m.createdAt")
    List<FacilityStockMovement> findForWorkOrder(@Param("tenantId") TenantId tenantId, @Param("workOrderId") UUID workOrderId);
}
