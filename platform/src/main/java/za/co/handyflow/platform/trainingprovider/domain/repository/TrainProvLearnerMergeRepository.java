package za.co.handyflow.platform.trainingprovider.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvLearnerMerge;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrainProvLearnerMergeRepository extends JpaRepository<TrainProvLearnerMerge, UUID> {

    @Query("""
        SELECT m FROM TrainProvLearnerMerge m WHERE m.tenantId = :#{#tenantId.value}
        AND (:status IS NULL OR m.status = :status) ORDER BY m.proposedAt DESC
        """)
    List<TrainProvLearnerMerge> search(TenantId tenantId, String status);

    @Query("SELECT m FROM TrainProvLearnerMerge m WHERE m.tenantId = :#{#tenantId.value} AND m.id = :id")
    Optional<TrainProvLearnerMerge> findForTenant(TenantId tenantId, UUID id);

    @Query("SELECT COUNT(m) > 0 FROM TrainProvLearnerMerge m WHERE m.tenantId = :#{#tenantId.value} AND m.mergedId = :mergedId AND m.status = 'PROPOSED'")
    boolean openFor(TenantId tenantId, UUID mergedId);

    @Query("SELECT m FROM TrainProvLearnerMerge m WHERE m.tenantId = :#{#tenantId.value} AND (m.survivorId = :learnerId OR m.mergedId = :learnerId) ORDER BY m.proposedAt")
    List<TrainProvLearnerMerge> forLearner(TenantId tenantId, UUID learnerId);
}
