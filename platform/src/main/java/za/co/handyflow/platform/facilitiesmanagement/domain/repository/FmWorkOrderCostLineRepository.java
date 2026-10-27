package za.co.handyflow.platform.facilitiesmanagement.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmWorkOrderCostLine;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FmWorkOrderCostLineRepository extends JpaRepository<FmWorkOrderCostLine, UUID> {

    @Query("SELECT c FROM FmWorkOrderCostLine c WHERE c.tenantId.value = :#{#tenantId.value} " +
            "AND c.workOrderId = :workOrderId ORDER BY c.recordedAt ASC")
    List<FmWorkOrderCostLine> findForWorkOrder(@Param("tenantId") TenantId tenantId, @Param("workOrderId") UUID workOrderId);

    @Query("SELECT c FROM FmWorkOrderCostLine c WHERE c.tenantId.value = :#{#tenantId.value} " +
            "AND c.workOrderId = :workOrderId AND c.id = :id")
    Optional<FmWorkOrderCostLine> findByIdForWorkOrder(@Param("tenantId") TenantId tenantId,
                                                       @Param("workOrderId") UUID workOrderId, @Param("id") UUID id);

    @Query("SELECT COUNT(c) FROM FmWorkOrderCostLine c WHERE c.tenantId.value = :#{#tenantId.value} AND c.workOrderId = :workOrderId")
    long countForWorkOrder(@Param("tenantId") TenantId tenantId, @Param("workOrderId") UUID workOrderId);

    /** What the client is charged: the CHARGE on BILLABLE lines only. Null when there are none. */
    @Query("SELECT SUM(c.totalCharge) FROM FmWorkOrderCostLine c WHERE c.tenantId.value = :#{#tenantId.value} " +
            "AND c.workOrderId = :workOrderId AND c.billable = true")
    BigDecimal sumBillableChargeForWorkOrder(@Param("tenantId") TenantId tenantId, @Param("workOrderId") UUID workOrderId);

    /** What the work cost the FM company: the COST of ALL lines, billable or not. Null when there are none. */
    @Query("SELECT SUM(c.totalCost) FROM FmWorkOrderCostLine c WHERE c.tenantId.value = :#{#tenantId.value} " +
            "AND c.workOrderId = :workOrderId")
    BigDecimal sumCostForWorkOrder(@Param("tenantId") TenantId tenantId, @Param("workOrderId") UUID workOrderId);
}