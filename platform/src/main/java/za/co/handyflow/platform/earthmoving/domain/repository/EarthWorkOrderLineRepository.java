package za.co.handyflow.platform.earthmoving.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.earthmoving.domain.model.EarthWorkOrderLine;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

public interface EarthWorkOrderLineRepository extends JpaRepository<EarthWorkOrderLine, UUID> {

    @Query("SELECT l FROM EarthWorkOrderLine l WHERE l.tenantId = :tenantId AND l.workOrderId = :workOrderId ORDER BY l.createdAt")
    List<EarthWorkOrderLine> findForWorkOrder(TenantId tenantId, UUID workOrderId);
}
