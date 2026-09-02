package za.co.handyflow.platform.training.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.training.domain.model.TrainingSession;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrainingSessionRepository extends JpaRepository<TrainingSession, UUID> {

    @Query("""
        SELECT s FROM TrainingSession s
        WHERE s.tenantId = :#{#tenantId.value}
        AND (:courseId IS NULL OR s.courseId = :courseId)
        AND (:status IS NULL OR s.status = :status)
        ORDER BY s.startDate DESC
        """)
    Page<TrainingSession> findAll(TenantId tenantId, UUID courseId, String status, Pageable pageable);

    @Query("SELECT s FROM TrainingSession s WHERE s.tenantId = :#{#tenantId.value} AND s.id = :id")
    Optional<TrainingSession> findByTenantAndId(TenantId tenantId, UUID id);

    @Query("""
        SELECT s FROM TrainingSession s
        WHERE s.tenantId = :#{#tenantId.value}
        AND s.status IN ('SCHEDULED', 'IN_PROGRESS')
        AND s.startDate <= :cutoff
        """)
    List<TrainingSession> findDueToStartOrOverdue(TenantId tenantId, LocalDate cutoff);

    /**
     * Cross-tenant sweep query for {@code TrainingNotificationScheduler} —
     * deliberately not tenant-scoped, matching the "grouped by
     * TenantId.of(entity.getTenantId())" convention used by every other
     * daily scheduler in this codebase's plain-entity-convention modules.
     * Upcoming = SCHEDULED sessions starting within the lookahead window.
     */
    @Query("""
        SELECT s FROM TrainingSession s
        WHERE s.status = 'SCHEDULED'
        AND s.startDate BETWEEN :from AND :to
        """)
    List<TrainingSession> findUpcomingAcrossTenants(LocalDate from, LocalDate to);
}
