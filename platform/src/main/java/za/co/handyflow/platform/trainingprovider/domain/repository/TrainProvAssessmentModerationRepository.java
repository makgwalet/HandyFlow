package za.co.handyflow.platform.trainingprovider.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvAssessmentAttempt;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvAssessmentModeration;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrainProvAssessmentModerationRepository extends JpaRepository<TrainProvAssessmentModeration, UUID> {

    @Query("SELECT m FROM TrainProvAssessmentModeration m WHERE m.tenantId = :#{#tenantId.value} AND m.attemptId = :attemptId")
    Optional<TrainProvAssessmentModeration> findForAttempt(TenantId tenantId, UUID attemptId);

    @Query("SELECT m FROM TrainProvAssessmentModeration m WHERE m.tenantId = :#{#tenantId.value} AND m.id = :id")
    Optional<TrainProvAssessmentModeration> findForTenant(TenantId tenantId, UUID id);

    @Query("""
        SELECT m FROM TrainProvAssessmentModeration m WHERE m.tenantId = :#{#tenantId.value}
        AND (:outcome IS NULL OR m.outcome = :outcome)
        AND (:openQueriesOnly = false OR (m.outcome = 'QUERIED' AND m.resolvedAt IS NULL))
        ORDER BY m.moderationNo DESC
        """)
    List<TrainProvAssessmentModeration> search(TenantId tenantId, String outcome, boolean openQueriesOnly);

    /** Decided, live attempts on instruments that are moderated and have not been moderated yet. */
    @Query("""
        SELECT a FROM TrainProvAssessmentAttempt a WHERE a.tenantId = :#{#tenantId.value} AND a.status = 'RECORDED'
        AND EXISTS (SELECT 1 FROM TrainProvAssessmentDecision d WHERE d.tenantId = a.tenantId AND d.attemptId = a.id)
        AND NOT EXISTS (SELECT 1 FROM TrainProvAssessmentModeration m WHERE m.tenantId = a.tenantId AND m.attemptId = a.id)
        AND EXISTS (SELECT 1 FROM TrainProvAssessmentInstrument i WHERE i.tenantId = a.tenantId AND i.id = a.instrumentId AND i.requiresModeration = true)
        ORDER BY a.assessedOn, a.attemptNo
        """)
    List<TrainProvAssessmentAttempt> pending(TenantId tenantId);
}
