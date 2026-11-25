package za.co.handyflow.platform.trainingprovider.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvCohortMember;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrainProvCohortMemberRepository extends JpaRepository<TrainProvCohortMember, UUID> {

    @Query("SELECT m FROM TrainProvCohortMember m WHERE m.tenantId = :#{#tenantId.value} AND m.cohortId = :cohortId ORDER BY m.status, m.addedAt")
    List<TrainProvCohortMember> findForCohort(TenantId tenantId, UUID cohortId);

    @Query("SELECT m FROM TrainProvCohortMember m WHERE m.tenantId = :#{#tenantId.value} AND m.id = :id")
    Optional<TrainProvCohortMember> findForTenant(TenantId tenantId, UUID id);

    @Query("SELECT COUNT(m) FROM TrainProvCohortMember m WHERE m.tenantId = :#{#tenantId.value} AND m.cohortId = :cohortId AND m.status = 'ACTIVE'")
    long countActive(TenantId tenantId, UUID cohortId);

    @Query("SELECT COUNT(m) > 0 FROM TrainProvCohortMember m WHERE m.tenantId = :#{#tenantId.value} AND m.enrolmentId = :enrolmentId AND m.status = 'ACTIVE'")
    boolean liveForEnrolment(TenantId tenantId, UUID enrolmentId);

    @Query("""
        SELECT m FROM TrainProvCohortMember m WHERE m.tenantId = :#{#tenantId.value}
        AND m.enrolmentId IN (SELECT e.id FROM TrainProvLearnerEnrolment e WHERE e.tenantId = :#{#tenantId.value} AND e.learnerId = :learnerId)
        ORDER BY m.addedAt DESC
        """)
    List<TrainProvCohortMember> findForLearner(TenantId tenantId, UUID learnerId);
}
