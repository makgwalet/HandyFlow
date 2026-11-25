package za.co.handyflow.platform.trainingprovider.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvCohortEvent;

import java.util.List;
import java.util.UUID;

public interface TrainProvCohortEventRepository extends JpaRepository<TrainProvCohortEvent, UUID> {

    @Query("SELECT e FROM TrainProvCohortEvent e WHERE e.tenantId = :#{#tenantId.value} AND e.cohortId = :cohortId ORDER BY e.actedAt")
    List<TrainProvCohortEvent> findForCohort(TenantId tenantId, UUID cohortId);
}
