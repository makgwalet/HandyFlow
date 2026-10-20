package za.co.handyflow.platform.fleet.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fleet.domain.model.WorkOrder;
import za.co.handyflow.platform.fleet.domain.model.WorkOrderStatus;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkOrderRepository extends JpaRepository<WorkOrder, UUID> {

    @Query("SELECT w FROM WorkOrder w WHERE w.tenantId = :tenantId AND w.id = :id")
    Optional<WorkOrder> findByTenantAndId(TenantId tenantId, UUID id);

    @Query("SELECT w FROM WorkOrder w WHERE w.tenantId = :tenantId ORDER BY w.createdAt DESC")
    Page<WorkOrder> findAllForTenant(TenantId tenantId, Pageable pageable);

    @Query("SELECT w FROM WorkOrder w WHERE w.tenantId = :tenantId AND w.status = :status ORDER BY w.createdAt DESC")
    Page<WorkOrder> findByStatus(TenantId tenantId, WorkOrderStatus status, Pageable pageable);

    @Query("SELECT w FROM WorkOrder w WHERE w.tenantId = :tenantId AND w.vehicleId = :vehicleId ORDER BY w.createdAt DESC")
    Page<WorkOrder> findByVehicle(TenantId tenantId, UUID vehicleId, Pageable pageable);

    @Query("SELECT w FROM WorkOrder w WHERE w.tenantId = :tenantId AND w.vehicleId = :vehicleId " +
            "AND w.status = :status ORDER BY w.createdAt DESC")
    Page<WorkOrder> findByVehicleAndStatus(TenantId tenantId, UUID vehicleId, WorkOrderStatus status, Pageable pageable);

    /** Other orders of the given statuses on this vehicle — used to decide whether it can leave MAINTENANCE. */
    @Query("SELECT COUNT(w) FROM WorkOrder w WHERE w.tenantId = :tenantId AND w.vehicleId = :vehicleId " +
            "AND w.id <> :excludeId AND w.status IN :statuses")
    long countByVehicleAndStatusesExcluding(TenantId tenantId, UUID vehicleId, UUID excludeId,
                                            Collection<WorkOrderStatus> statuses);

    @Query("SELECT w FROM WorkOrder w WHERE w.tenantId = :tenantId AND w.issueId = :issueId AND w.status IN :statuses")
    List<WorkOrder> findByIssueAndStatuses(TenantId tenantId, UUID issueId, Collection<WorkOrderStatus> statuses);

    @Query("SELECT COUNT(w) FROM WorkOrder w WHERE w.tenantId = :tenantId AND w.issueId = :issueId " +
            "AND w.id <> :excludeId AND w.status IN :statuses")
    long countByIssueAndStatusesExcluding(TenantId tenantId, UUID issueId, UUID excludeId,
                                          Collection<WorkOrderStatus> statuses);
}