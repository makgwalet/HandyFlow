package za.co.handyflow.platform.trainingprovider.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvLearnerApplicationEvent;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrainProvLearnerApplicationEventRepository extends JpaRepository<TrainProvLearnerApplicationEvent, UUID> {

    @Query("SELECT e FROM TrainProvLearnerApplicationEvent e WHERE e.tenantId = :#{#tenantId.value} AND e.applicationId = :applicationId ORDER BY e.actedAt")
    List<TrainProvLearnerApplicationEvent> findForApplication(TenantId tenantId, UUID applicationId);
}
