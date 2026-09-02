package za.co.handyflow.platform.training.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.training.domain.model.TrainingEnrollment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrainingEnrollmentRepository extends JpaRepository<TrainingEnrollment, UUID> {

    @Query("""
        SELECT e FROM TrainingEnrollment e
        WHERE e.tenantId = :#{#tenantId.value}
        AND (:sessionId IS NULL OR e.sessionId = :sessionId)
        AND (:employeeId IS NULL OR e.employeeId = :employeeId)
        AND (:status IS NULL OR e.status = :status)
        ORDER BY e.enrolledAt DESC
        """)
    Page<TrainingEnrollment> findAll(TenantId tenantId, UUID sessionId, UUID employeeId, String status, Pageable pageable);

    @Query("SELECT e FROM TrainingEnrollment e WHERE e.tenantId = :#{#tenantId.value} AND e.id = :id")
    Optional<TrainingEnrollment> findByTenantAndId(TenantId tenantId, UUID id);

    @Query("""
        SELECT COUNT(e) FROM TrainingEnrollment e
        WHERE e.tenantId = :#{#tenantId.value} AND e.sessionId = :sessionId
        AND e.status IN ('ENROLLED', 'ATTENDED', 'COMPLETED')
        """)
    long countLiveBySession(TenantId tenantId, UUID sessionId);

    @Query("""
        SELECT e FROM TrainingEnrollment e
        WHERE e.tenantId = :#{#tenantId.value} AND e.sessionId = :sessionId
        AND e.employeeId = :employeeId
        AND e.status NOT IN ('CANCELLED')
        """)
    Optional<TrainingEnrollment> findActiveEnrollment(TenantId tenantId, UUID sessionId, UUID employeeId);

    @Query("SELECT e FROM TrainingEnrollment e WHERE e.tenantId = :#{#tenantId.value} AND e.employeeId = :employeeId ORDER BY e.enrolledAt DESC")
    List<TrainingEnrollment> findAllForEmployee(TenantId tenantId, UUID employeeId);

    @Query("SELECT e FROM TrainingEnrollment e WHERE e.tenantId = :#{#tenantId.value} AND e.sessionId = :sessionId ORDER BY e.employeeNameSnapshot")
    List<TrainingEnrollment> findAllForSession(TenantId tenantId, UUID sessionId);
}
