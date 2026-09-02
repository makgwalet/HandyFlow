package za.co.handyflow.platform.trainingprovider.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvEnrollment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrainProvEnrollmentRepository extends JpaRepository<TrainProvEnrollment, UUID> {

    @Query("""
        SELECT e FROM TrainProvEnrollment e
        WHERE e.tenantId = :#{#tenantId.value}
        AND (:sessionId IS NULL OR e.sessionId = :sessionId)
        AND (:clientId IS NULL OR e.clientId = :clientId)
        AND (:status IS NULL OR e.status = :status)
        ORDER BY e.enrolledAt DESC
        """)
    Page<TrainProvEnrollment> findAll(TenantId tenantId, UUID sessionId, UUID clientId, String status, Pageable pageable);

    @Query("SELECT e FROM TrainProvEnrollment e WHERE e.tenantId = :#{#tenantId.value} AND e.id = :id")
    Optional<TrainProvEnrollment> findByTenantAndId(TenantId tenantId, UUID id);

    @Query("""
        SELECT COUNT(e) FROM TrainProvEnrollment e
        WHERE e.tenantId = :#{#tenantId.value} AND e.sessionId = :sessionId
        AND e.status IN ('ENROLLED', 'ATTENDED', 'COMPLETED')
        """)
    long countLiveBySession(TenantId tenantId, UUID sessionId);

    @Query("""
        SELECT e FROM TrainProvEnrollment e
        WHERE e.tenantId = :#{#tenantId.value} AND e.sessionId = :sessionId AND e.delegateId = :delegateId
        AND e.status <> 'CANCELLED'
        """)
    Optional<TrainProvEnrollment> findActiveEnrollment(TenantId tenantId, UUID sessionId, UUID delegateId);

    @Query("SELECT e FROM TrainProvEnrollment e WHERE e.tenantId = :#{#tenantId.value} AND e.sessionId = :sessionId ORDER BY e.delegateNameSnapshot")
    List<TrainProvEnrollment> findAllForSession(TenantId tenantId, UUID sessionId);

    /** The billing engine's own source query: every not-yet-invoiced, billable enrollment for a client whose session has already started by the invoice's issue date. */
    @Query("""
        SELECT e FROM TrainProvEnrollment e
        JOIN TrainProvSession s ON s.id = e.sessionId
        WHERE e.tenantId = :#{#tenantId.value} AND e.clientId = :clientId
        AND e.invoiced = false AND e.status <> 'CANCELLED'
        AND s.startDate <= :asOf
        """)
    List<TrainProvEnrollment> findBillableForClient(TenantId tenantId, UUID clientId, java.time.LocalDate asOf);
}
