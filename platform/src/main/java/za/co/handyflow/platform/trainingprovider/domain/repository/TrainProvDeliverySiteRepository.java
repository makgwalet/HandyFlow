package za.co.handyflow.platform.trainingprovider.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvDeliverySite;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrainProvDeliverySiteRepository extends JpaRepository<TrainProvDeliverySite, UUID> {

    @Query("SELECT s FROM TrainProvDeliverySite s WHERE s.tenantId = :#{#tenantId.value} ORDER BY s.status, s.name")
    List<TrainProvDeliverySite> findAllForTenant(TenantId tenantId);

    @Query("SELECT s FROM TrainProvDeliverySite s WHERE s.tenantId = :#{#tenantId.value} AND s.id = :id")
    Optional<TrainProvDeliverySite> findForTenant(TenantId tenantId, UUID id);

    @Query("SELECT COUNT(s) > 0 FROM TrainProvDeliverySite s WHERE s.tenantId = :#{#tenantId.value} AND LOWER(s.name) = LOWER(:name) AND (:excludeId IS NULL OR s.id <> :excludeId)")
    boolean nameTaken(TenantId tenantId, String name, UUID excludeId);
}
