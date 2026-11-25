package za.co.handyflow.platform.trainingprovider.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvAssessmentDecision;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrainProvAssessmentDecisionRepository extends JpaRepository<TrainProvAssessmentDecision, UUID> {

    @Query("SELECT d FROM TrainProvAssessmentDecision d WHERE d.tenantId = :#{#tenantId.value} AND d.attemptId = :attemptId")
    Optional<TrainProvAssessmentDecision> findForAttempt(TenantId tenantId, UUID attemptId);

    @Query("SELECT d FROM TrainProvAssessmentDecision d WHERE d.tenantId = :#{#tenantId.value} AND d.attemptId IN :attemptIds")
    List<TrainProvAssessmentDecision> findForAttempts(TenantId tenantId, Collection<UUID> attemptIds);
}
