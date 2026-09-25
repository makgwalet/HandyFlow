package za.co.handyflow.platform.compliancetender.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.compliancetender.domain.model.ComplianceRegistration;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ComplianceRegistrationRepository extends JpaRepository<ComplianceRegistration, UUID> {

    @Query("SELECT r FROM ComplianceRegistration r WHERE r.tenantId.value = :#{#tenantId.value} AND r.id = :id")
    Optional<ComplianceRegistration> findByIdForTenant(@Param("tenantId") TenantId tenantId, @Param("id") UUID id);

    @Query("SELECT r FROM ComplianceRegistration r WHERE r.tenantId.value = :#{#tenantId.value} " +
           "AND (:authority IS NULL OR r.authority = :authority) ORDER BY r.authority, r.registrationType")
    Page<ComplianceRegistration> findAll(@Param("tenantId") TenantId tenantId,
                                         @Param("authority") String authority, Pageable pageable);

    @Query("SELECT r FROM ComplianceRegistration r WHERE r.tenantId.value = :#{#tenantId.value} ORDER BY r.authority, r.registrationType")
    List<ComplianceRegistration> findAllForTenant(@Param("tenantId") TenantId tenantId);

    /** Cross-tenant sweep: every ACTIVE registration with an expiry date to check (expiring-soon and just-expired alerts). */
    @Query("SELECT r FROM ComplianceRegistration r WHERE r.status = 'ACTIVE' AND r.expiryDate IS NOT NULL AND r.expiryDate <= :cutoff")
    List<ComplianceRegistration> findAllActiveWithExpiryAcrossTenants(@Param("cutoff") LocalDate cutoff);
}
