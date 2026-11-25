package za.co.handyflow.platform.trainingprovider.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvLessonProgress;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrainProvLessonProgressRepository extends JpaRepository<TrainProvLessonProgress, UUID> {

    @Query("SELECT p FROM TrainProvLessonProgress p WHERE p.tenantId = :#{#tenantId.value} AND p.enrolmentId = :enrolmentId ORDER BY p.createdAt DESC")
    List<TrainProvLessonProgress> forEnrolment(TenantId tenantId, UUID enrolmentId);

    @Query("SELECT p FROM TrainProvLessonProgress p WHERE p.tenantId = :#{#tenantId.value} AND p.id = :id")
    Optional<TrainProvLessonProgress> findForTenant(TenantId tenantId, UUID id);

    @Query("SELECT COUNT(p) > 0 FROM TrainProvLessonProgress p WHERE p.tenantId = :#{#tenantId.value} AND p.enrolmentId = :enrolmentId AND p.lessonId = :lessonId AND p.status = 'FINISHED'")
    boolean finishedExists(TenantId tenantId, UUID enrolmentId, UUID lessonId);
}
