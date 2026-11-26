package za.co.handyflow.platform.trainingprovider.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvLearnerEvent;

import java.util.List;
import java.util.UUID;

public interface TrainProvLearnerEventRepository extends JpaRepository<TrainProvLearnerEvent, UUID> {

    @Query("SELECT e FROM TrainProvLearnerEvent e WHERE e.tenantId = :#{#tenantId.value} AND e.learnerId = :learnerId ORDER BY e.actedAt")
    List<TrainProvLearnerEvent> forLearner(TenantId tenantId, UUID learnerId);
}
