package za.co.handyflow.platform.earthmoving.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.earthmoving.domain.model.EarthWorkOrder;
import za.co.handyflow.platform.earthmoving.domain.model.WorkOrderStatus;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EarthWorkOrderRepository extends JpaRepository<EarthWorkOrder, UUID> {

    @Query("SELECT w FROM EarthWorkOrder w WHERE w.tenantId = :tenantId AND w.id = :id")
    Optional<EarthWorkOrder> findByTenantAndId(TenantId tenantId, UUID id);

    @Query("SELECT w FROM EarthWorkOrder w WHERE w.tenantId = :tenantId ORDER BY w.createdAt DESC")
    Page<EarthWorkOrder> findAllForTenant(TenantId tenantId, Pageable pageable);

    @Query("SELECT w FROM EarthWorkOrder w WHERE w.tenantId = :tenantId AND w.status = :status ORDER BY w.createdAt DESC")
    Page<EarthWorkOrder> findByStatus(TenantId tenantId, WorkOrderStatus status, Pageable pageable);

    @Query("SELECT w FROM EarthWorkOrder w WHERE w.tenantId = :tenantId AND w.assetId = :assetId ORDER BY w.createdAt DESC")
    Page<EarthWorkOrder> findByAsset(TenantId tenantId, UUID assetId, Pageable pageable);

    /** Open work orders that block release on a machine (patch E2): release is refused while any exist. */
    @Query("SELECT w FROM EarthWorkOrder w WHERE w.tenantId = :tenantId AND w.assetId = :assetId AND w.blocksRelease = true "
            + "AND w.status NOT IN (za.co.handyflow.platform.earthmoving.domain.model.WorkOrderStatus.COMPLETED, "
            + "za.co.handyflow.platform.earthmoving.domain.model.WorkOrderStatus.CANCELLED) ORDER BY w.createdAt")
    List<EarthWorkOrder> findOpenBlocking(TenantId tenantId, UUID assetId);

    @Query("SELECT w FROM EarthWorkOrder w WHERE w.tenantId = :tenantId AND w.assetId = :assetId "
            + "AND w.status = za.co.handyflow.platform.earthmoving.domain.model.WorkOrderStatus.COMPLETED ORDER BY w.completedAt DESC")
    List<EarthWorkOrder> findCompletedForAsset(TenantId tenantId, UUID assetId, Pageable pageable);

    @Query("SELECT COUNT(w) > 0 FROM EarthWorkOrder w WHERE w.planId = :planId "
            + "AND w.status NOT IN (za.co.handyflow.platform.earthmoving.domain.model.WorkOrderStatus.COMPLETED, "
            + "za.co.handyflow.platform.earthmoving.domain.model.WorkOrderStatus.CANCELLED)")
    boolean existsOpenForPlan(UUID planId);
}
