package za.co.handyflow.platform.trainingprovider.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvAssessmentAttempt;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrainProvAssessmentAttemptRepository extends JpaRepository<TrainProvAssessmentAttempt, UUID> {

    @Query("""
        SELECT a FROM TrainProvAssessmentAttempt a WHERE a.tenantId = :#{#tenantId.value}
        AND (CAST(:cohortId AS uuid) IS NULL OR a.cohortId = :cohortId)
        AND (CAST(:instrumentId AS uuid) IS NULL OR a.instrumentId = :instrumentId)
        AND (CAST(:enrolmentId AS uuid) IS NULL OR a.enrolmentId = :enrolmentId)
        ORDER BY a.attemptNo DESC
        """)
    List<TrainProvAssessmentAttempt> search(TenantId tenantId, UUID cohortId, UUID instrumentId, UUID enrolmentId);

    @Query("SELECT a FROM TrainProvAssessmentAttempt a WHERE a.tenantId = :#{#tenantId.value} AND a.id = :id")
    Optional<TrainProvAssessmentAttempt> findForTenant(TenantId tenantId, UUID id);

    @Query("SELECT a FROM TrainProvAssessmentAttempt a WHERE a.tenantId = :#{#tenantId.value} AND a.instrumentId = :instrumentId AND a.memberId = :memberId ORDER BY a.attemptSeq")
    List<TrainProvAssessmentAttempt> findForMemberInstrument(TenantId tenantId, UUID instrumentId, UUID memberId);
}
