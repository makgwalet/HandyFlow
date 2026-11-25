package za.co.handyflow.platform.trainingprovider.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvLearnerEnrolmentEvent;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrainProvLearnerEnrolmentEventRepository extends JpaRepository<TrainProvLearnerEnrolmentEvent, UUID> {

    @Query("SELECT e FROM TrainProvLearnerEnrolmentEvent e WHERE e.tenantId = :#{#tenantId.value} AND e.enrolmentId = :enrolmentId ORDER BY e.actedAt")
    List<TrainProvLearnerEnrolmentEvent> findForEnrolment(TenantId tenantId, UUID enrolmentId);
}
