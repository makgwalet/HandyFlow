package za.co.handyflow.platform.training.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.training.domain.model.TrainingCertificate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrainingCertificateRepository extends JpaRepository<TrainingCertificate, UUID> {

    @Query("""
        SELECT c FROM TrainingCertificate c
        WHERE c.tenantId = :#{#tenantId.value}
        AND (:employeeId IS NULL OR c.employeeId = :employeeId)
        AND (:status IS NULL OR c.status = :status)
        ORDER BY c.issueDate DESC
        """)
    Page<TrainingCertificate> findAll(TenantId tenantId, UUID employeeId, String status, Pageable pageable);

    @Query("SELECT c FROM TrainingCertificate c WHERE c.tenantId = :#{#tenantId.value} AND c.id = :id")
    Optional<TrainingCertificate> findByTenantAndId(TenantId tenantId, UUID id);

    @Query("SELECT c FROM TrainingCertificate c WHERE c.tenantId = :#{#tenantId.value} AND c.enrollmentId = :enrollmentId")
    Optional<TrainingCertificate> findByEnrollmentId(TenantId tenantId, UUID enrollmentId);

    /** Cross-tenant sweep for the daily expiry scan — see TrainingSessionRepository.findUpcomingAcrossTenants for the same convention. */
    @Query("SELECT c FROM TrainingCertificate c WHERE c.status = 'VALID' AND c.expiryDate IS NOT NULL")
    List<TrainingCertificate> findAllValidWithExpiryAcrossTenants();
}
