package za.co.handyflow.platform.trainingprovider.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvSamplingAction;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrainProvSamplingActionRepository extends JpaRepository<TrainProvSamplingAction, UUID> {

    @Query("SELECT a FROM TrainProvSamplingAction a WHERE a.tenantId = :#{#tenantId.value} AND a.drawId = :drawId ORDER BY a.createdAt")
    List<TrainProvSamplingAction> forDraw(TenantId tenantId, UUID drawId);

    @Query("SELECT a FROM TrainProvSamplingAction a WHERE a.tenantId = :#{#tenantId.value} AND a.id = :id")
    Optional<TrainProvSamplingAction> findForTenant(TenantId tenantId, UUID id);

    @Query("SELECT COUNT(a) FROM TrainProvSamplingAction a WHERE a.tenantId = :#{#tenantId.value} AND a.drawId = :drawId AND a.status = 'OPEN'")
    long openFor(TenantId tenantId, UUID drawId);
}
