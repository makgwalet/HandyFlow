package za.co.handyflow.platform.trainingprovider.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvAccreditationEvent;

import java.util.List;
import java.util.UUID;

public interface TrainProvAccreditationEventRepository extends JpaRepository<TrainProvAccreditationEvent, UUID> {

    @Query("SELECT e FROM TrainProvAccreditationEvent e WHERE e.tenantId = :#{#tenantId.value} AND e.accreditationId = :accreditationId ORDER BY e.actedAt")
    List<TrainProvAccreditationEvent> findForAccreditation(TenantId tenantId, UUID accreditationId);
}
