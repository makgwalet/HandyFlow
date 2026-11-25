package za.co.handyflow.platform.trainingprovider.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvLearnerEnrolment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrainProvLearnerEnrolmentRepository extends JpaRepository<TrainProvLearnerEnrolment, UUID> {

    @Query("""
        SELECT e FROM TrainProvLearnerEnrolment e WHERE e.tenantId = :#{#tenantId.value}
        AND (:status IS NULL OR e.status = :status)
        AND (CAST(:learnerId AS uuid) IS NULL OR e.learnerId = :learnerId)
        AND (CAST(:programmeId AS uuid) IS NULL OR e.programmeId = :programmeId)
        ORDER BY e.createdAt DESC
        """)
    List<TrainProvLearnerEnrolment> search(TenantId tenantId, String status, UUID learnerId, UUID programmeId);

    @Query("SELECT e FROM TrainProvLearnerEnrolment e WHERE e.tenantId = :#{#tenantId.value} AND e.id = :id")
    Optional<TrainProvLearnerEnrolment> findForTenant(TenantId tenantId, UUID id);

    @Query("""
        SELECT COUNT(e) > 0 FROM TrainProvLearnerEnrolment e WHERE e.tenantId = :#{#tenantId.value}
        AND e.learnerId = :learnerId AND e.programmeId = :programmeId AND e.status IN ('ENROLLED', 'IN_PROGRESS', 'ON_HOLD')
        """)
    boolean liveExists(TenantId tenantId, UUID learnerId, UUID programmeId);

    @Query("SELECT COUNT(e) FROM TrainProvLearnerEnrolment e WHERE e.tenantId = :#{#tenantId.value} AND e.learnerId = :learnerId AND e.status IN ('ENROLLED', 'IN_PROGRESS', 'ON_HOLD')")
    long countLiveForLearner(TenantId tenantId, UUID learnerId);

    @Query("SELECT COUNT(e) FROM TrainProvLearnerEnrolment e WHERE e.tenantId = :#{#tenantId.value} AND e.programmeId = :programmeId AND e.status IN ('ENROLLED', 'IN_PROGRESS', 'ON_HOLD')")
    long countLiveForProgramme(TenantId tenantId, UUID programmeId);
}
