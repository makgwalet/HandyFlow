package za.co.handyflow.platform.trainingprovider.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvSession;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrainProvSessionRepository extends JpaRepository<TrainProvSession, UUID> {

    @Query("""
        SELECT s FROM TrainProvSession s
        WHERE s.tenantId = :#{#tenantId.value}
        AND (:courseId IS NULL OR s.courseId = :courseId)
        AND (:clientId IS NULL OR s.clientId = :clientId)
        AND (:status IS NULL OR s.status = :status)
        ORDER BY s.startDate DESC
        """)
    Page<TrainProvSession> findAll(TenantId tenantId, UUID courseId, UUID clientId, String status, Pageable pageable);

    @Query("SELECT s FROM TrainProvSession s WHERE s.tenantId = :#{#tenantId.value} AND s.id = :id")
    Optional<TrainProvSession> findByTenantAndId(TenantId tenantId, UUID id);

    /** Cross-tenant sweep for the daily scheduler — same convention as Module 4a's own TrainingSessionRepository.findUpcomingAcrossTenants. */
    @Query("""
        SELECT s FROM TrainProvSession s
        WHERE s.status = 'SCHEDULED'
        AND s.startDate BETWEEN :from AND :to
        """)
    List<TrainProvSession> findUpcomingAcrossTenants(LocalDate from, LocalDate to);
}
