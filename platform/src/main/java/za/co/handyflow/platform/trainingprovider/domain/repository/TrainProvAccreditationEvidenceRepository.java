package za.co.handyflow.platform.trainingprovider.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvAccreditationEvidence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrainProvAccreditationEvidenceRepository extends JpaRepository<TrainProvAccreditationEvidence, UUID> {

    @Query("SELECT e FROM TrainProvAccreditationEvidence e WHERE e.tenantId = :#{#tenantId.value} AND e.accreditationId = :accreditationId ORDER BY e.receivedOn DESC, e.addedAt DESC")
    List<TrainProvAccreditationEvidence> forAccreditation(TenantId tenantId, UUID accreditationId);

    @Query("SELECT e FROM TrainProvAccreditationEvidence e WHERE e.tenantId = :#{#tenantId.value} AND e.id = :id")
    Optional<TrainProvAccreditationEvidence> findForTenant(TenantId tenantId, UUID id);

    @Query("SELECT e FROM TrainProvAccreditationEvidence e WHERE e.tenantId = :#{#tenantId.value}")
    List<TrainProvAccreditationEvidence> allForTenant(TenantId tenantId);
}
