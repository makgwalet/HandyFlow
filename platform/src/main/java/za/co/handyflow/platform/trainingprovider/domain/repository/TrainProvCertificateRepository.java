package za.co.handyflow.platform.trainingprovider.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvCertificate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrainProvCertificateRepository extends JpaRepository<TrainProvCertificate, UUID> {

    @Query("""
        SELECT c FROM TrainProvCertificate c
        WHERE c.tenantId = :#{#tenantId.value}
        AND (:clientId IS NULL OR c.clientId = :clientId)
        AND (:status IS NULL OR c.status = :status)
        ORDER BY c.issueDate DESC
        """)
    Page<TrainProvCertificate> findAll(TenantId tenantId, UUID clientId, String status, Pageable pageable);

    @Query("SELECT c FROM TrainProvCertificate c WHERE c.tenantId = :#{#tenantId.value} AND c.id = :id")
    Optional<TrainProvCertificate> findByTenantAndId(TenantId tenantId, UUID id);

    @Query("SELECT c FROM TrainProvCertificate c WHERE c.tenantId = :#{#tenantId.value} AND c.enrollmentId = :enrollmentId")
    Optional<TrainProvCertificate> findByEnrollmentId(TenantId tenantId, UUID enrollmentId);

    @Query("SELECT c FROM TrainProvCertificate c WHERE c.status = 'VALID' AND c.expiryDate IS NOT NULL")
    List<TrainProvCertificate> findAllValidWithExpiryAcrossTenants();
}
