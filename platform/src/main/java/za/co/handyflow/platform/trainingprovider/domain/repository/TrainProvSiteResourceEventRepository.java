package za.co.handyflow.platform.trainingprovider.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvSiteResourceEvent;

import java.util.List;
import java.util.UUID;

public interface TrainProvSiteResourceEventRepository extends JpaRepository<TrainProvSiteResourceEvent, UUID> {

    @Query("SELECT e FROM TrainProvSiteResourceEvent e WHERE e.tenantId = :#{#tenantId.value} AND e.resourceId = :resourceId ORDER BY e.createdAt DESC")
    List<TrainProvSiteResourceEvent> forResource(TenantId tenantId, UUID resourceId);
}
