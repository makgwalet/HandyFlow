package za.co.handyflow.platform.trainingprovider.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvSamplingDrawItem;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrainProvSamplingDrawItemRepository extends JpaRepository<TrainProvSamplingDrawItem, UUID> {

    @Query("SELECT i FROM TrainProvSamplingDrawItem i WHERE i.tenantId = :#{#tenantId.value} AND i.drawId = :drawId")
    List<TrainProvSamplingDrawItem> forDraw(TenantId tenantId, UUID drawId);

    @Query("SELECT i FROM TrainProvSamplingDrawItem i WHERE i.tenantId = :#{#tenantId.value} AND i.id = :id")
    Optional<TrainProvSamplingDrawItem> findForTenant(TenantId tenantId, UUID id);
}
