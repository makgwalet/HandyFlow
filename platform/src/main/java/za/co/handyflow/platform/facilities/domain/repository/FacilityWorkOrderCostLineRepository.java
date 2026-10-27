package za.co.handyflow.platform.facilities.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.facilities.domain.model.FacilityWorkOrderCostLine;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FacilityWorkOrderCostLineRepository extends JpaRepository<FacilityWorkOrderCostLine, UUID> {

    @Query("SELECT c FROM FacilityWorkOrderCostLine c WHERE c.tenantId.value = :#{#tenantId.value} " +
            "AND c.workOrderId = :workOrderId ORDER BY c.recordedAt ASC")
    List<FacilityWorkOrderCostLine> findForWorkOrder(@Param("tenantId") TenantId tenantId, @Param("workOrderId") UUID workOrderId);

    @Query("SELECT c FROM FacilityWorkOrderCostLine c WHERE c.tenantId.value = :#{#tenantId.value} " +
            "AND c.workOrderId = :workOrderId AND c.id = :id")
    Optional<FacilityWorkOrderCostLine> findByIdForWorkOrder(@Param("tenantId") TenantId tenantId,
                                                             @Param("workOrderId") UUID workOrderId, @Param("id") UUID id);

    /** Null when the work order has no lines. */
    @Query("SELECT SUM(c.totalCost) FROM FacilityWorkOrderCostLine c WHERE c.tenantId.value = :#{#tenantId.value} " +
            "AND c.workOrderId = :workOrderId")
    BigDecimal sumForWorkOrder(@Param("tenantId") TenantId tenantId, @Param("workOrderId") UUID workOrderId);
}