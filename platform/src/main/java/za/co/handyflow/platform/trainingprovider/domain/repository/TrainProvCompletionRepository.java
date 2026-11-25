package za.co.handyflow.platform.trainingprovider.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvCompletion;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrainProvCompletionRepository extends JpaRepository<TrainProvCompletion, UUID> {

    @Query("""
        SELECT c FROM TrainProvCompletion c WHERE c.tenantId = :#{#tenantId.value}
        AND (CAST(:enrolmentId AS uuid) IS NULL OR c.enrolmentId = :enrolmentId)
        AND (:outcome IS NULL OR c.outcome = :outcome)
        AND (:status IS NULL OR c.status = :status)
        ORDER BY c.completionNo DESC
        """)
    List<TrainProvCompletion> search(TenantId tenantId, UUID enrolmentId, String outcome, String status);

    @Query("SELECT c FROM TrainProvCompletion c WHERE c.tenantId = :#{#tenantId.value} AND c.id = :id")
    Optional<TrainProvCompletion> findForTenant(TenantId tenantId, UUID id);

    @Query("SELECT c FROM TrainProvCompletion c WHERE c.tenantId = :#{#tenantId.value} AND c.enrolmentId = :enrolmentId AND c.status = 'RECORDED'")
    Optional<TrainProvCompletion> findLiveForEnrolment(TenantId tenantId, UUID enrolmentId);
}
