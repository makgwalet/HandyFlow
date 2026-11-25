package za.co.handyflow.platform.trainingprovider.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvRplEvidence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrainProvRplEvidenceRepository extends JpaRepository<TrainProvRplEvidence, UUID> {

    @Query("SELECT e FROM TrainProvRplEvidence e WHERE e.tenantId = :#{#tenantId.value} AND e.applicationId = :applicationId ORDER BY e.receivedOn, e.createdAt")
    List<TrainProvRplEvidence> forApplication(TenantId tenantId, UUID applicationId);

    @Query("SELECT e FROM TrainProvRplEvidence e WHERE e.tenantId = :#{#tenantId.value} AND e.id = :id")
    Optional<TrainProvRplEvidence> findForTenant(TenantId tenantId, UUID id);
}
