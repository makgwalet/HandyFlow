package za.co.handyflow.platform.trainingprovider.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvLearnerApplication;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrainProvLearnerApplicationRepository extends JpaRepository<TrainProvLearnerApplication, UUID> {

    @Query("""
        SELECT a FROM TrainProvLearnerApplication a WHERE a.tenantId = :#{#tenantId.value}
        AND (:status IS NULL OR a.status = :status)
        AND (CAST(:learnerId AS uuid) IS NULL OR a.learnerId = :learnerId)
        AND (CAST(:programmeId AS uuid) IS NULL OR a.programmeId = :programmeId)
        ORDER BY a.createdAt DESC
        """)
    List<TrainProvLearnerApplication> search(TenantId tenantId, String status, UUID learnerId, UUID programmeId);

    @Query("SELECT a FROM TrainProvLearnerApplication a WHERE a.tenantId = :#{#tenantId.value} AND a.id = :id")
    Optional<TrainProvLearnerApplication> findForTenant(TenantId tenantId, UUID id);

    @Query("""
        SELECT COUNT(a) > 0 FROM TrainProvLearnerApplication a WHERE a.tenantId = :#{#tenantId.value}
        AND a.learnerId = :learnerId AND a.programmeId = :programmeId
        AND a.status IN ('RECEIVED', 'UNDER_REVIEW', 'DOCUMENTS_REQUESTED', 'ACCEPTED')
        """)
    boolean openExists(TenantId tenantId, UUID learnerId, UUID programmeId);
}
