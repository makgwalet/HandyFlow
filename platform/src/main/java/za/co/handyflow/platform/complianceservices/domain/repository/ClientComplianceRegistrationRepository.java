package za.co.handyflow.platform.complianceservices.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.complianceservices.domain.model.ClientComplianceRegistration;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClientComplianceRegistrationRepository extends JpaRepository<ClientComplianceRegistration, UUID> {

    @Query("SELECT r FROM ClientComplianceRegistration r WHERE r.tenantId.value = :#{#tenantId.value} AND r.id = :id")
    Optional<ClientComplianceRegistration> findByIdForTenant(@Param("tenantId") TenantId tenantId, @Param("id") UUID id);

    @Query("SELECT r FROM ClientComplianceRegistration r WHERE r.tenantId.value = :#{#tenantId.value} " +
           "AND r.clientId = :clientId ORDER BY r.authority, r.registrationType")
    List<ClientComplianceRegistration> findByClient(@Param("tenantId") TenantId tenantId, @Param("clientId") UUID clientId);

    /** Cross-client, cross-tenant sweep — same pattern as compliancetender's own ComplianceRegistrationRepository. */
    @Query("SELECT r FROM ClientComplianceRegistration r WHERE r.status = 'ACTIVE' AND r.expiryDate IS NOT NULL AND r.expiryDate <= :cutoff")
    List<ClientComplianceRegistration> findAllActiveWithExpiryAcrossTenants(@Param("cutoff") LocalDate cutoff);
}
