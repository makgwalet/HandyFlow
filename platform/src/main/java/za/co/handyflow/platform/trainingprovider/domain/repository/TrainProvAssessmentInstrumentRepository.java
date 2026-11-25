package za.co.handyflow.platform.trainingprovider.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvAssessmentInstrument;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrainProvAssessmentInstrumentRepository extends JpaRepository<TrainProvAssessmentInstrument, UUID> {

    @Query("""
        SELECT i FROM TrainProvAssessmentInstrument i WHERE i.tenantId = :#{#tenantId.value}
        AND (CAST(:versionId AS uuid) IS NULL OR i.programmeVersionId = :versionId)
        AND (CAST(:moduleId AS uuid) IS NULL OR i.moduleId = :moduleId)
        AND (:status IS NULL OR i.status = :status)
        ORDER BY i.instrumentNo DESC
        """)
    List<TrainProvAssessmentInstrument> search(TenantId tenantId, UUID versionId, UUID moduleId, String status);

    @Query("SELECT i FROM TrainProvAssessmentInstrument i WHERE i.tenantId = :#{#tenantId.value} AND i.id = :id")
    Optional<TrainProvAssessmentInstrument> findForTenant(TenantId tenantId, UUID id);

    @Query("""
        SELECT COUNT(i) > 0 FROM TrainProvAssessmentInstrument i WHERE i.tenantId = :#{#tenantId.value} AND i.programmeVersionId = :versionId
        AND LOWER(i.title) = LOWER(:title) AND i.status <> 'RETIRED' AND (CAST(:excludeId AS uuid) IS NULL OR i.id <> :excludeId)
        """)
    boolean liveTitleTaken(TenantId tenantId, UUID versionId, String title, UUID excludeId);
}
