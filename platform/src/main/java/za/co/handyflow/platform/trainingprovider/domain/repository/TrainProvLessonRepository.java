package za.co.handyflow.platform.trainingprovider.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvLesson;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrainProvLessonRepository extends JpaRepository<TrainProvLesson, UUID> {

    @Query("""
        SELECT l FROM TrainProvLesson l WHERE l.tenantId = :#{#tenantId.value}
        AND (CAST(:versionId AS uuid) IS NULL OR l.programmeVersionId = :versionId)
        AND (CAST(:moduleId AS uuid) IS NULL OR l.moduleId = :moduleId)
        AND (:status IS NULL OR l.status = :status)
        ORDER BY l.positionNo, l.lessonNo
        """)
    List<TrainProvLesson> search(TenantId tenantId, UUID versionId, UUID moduleId, String status);

    @Query("SELECT l FROM TrainProvLesson l WHERE l.tenantId = :#{#tenantId.value} AND l.id = :id")
    Optional<TrainProvLesson> findForTenant(TenantId tenantId, UUID id);

    @Query("""
        SELECT COUNT(l) > 0 FROM TrainProvLesson l WHERE l.tenantId = :#{#tenantId.value} AND l.programmeVersionId = :versionId
        AND LOWER(l.title) = LOWER(:title) AND l.status <> 'RETIRED' AND (CAST(:excludeId AS uuid) IS NULL OR l.id <> :excludeId)
        """)
    boolean liveTitleTaken(TenantId tenantId, UUID versionId, String title, UUID excludeId);
}
