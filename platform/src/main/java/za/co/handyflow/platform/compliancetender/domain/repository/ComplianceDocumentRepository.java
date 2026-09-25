package za.co.handyflow.platform.compliancetender.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.compliancetender.domain.model.ComplianceDocument;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ComplianceDocumentRepository extends JpaRepository<ComplianceDocument, UUID> {

    @Query("SELECT d FROM ComplianceDocument d WHERE d.tenantId.value = :#{#tenantId.value} AND d.id = :id")
    Optional<ComplianceDocument> findByIdForTenant(@Param("tenantId") TenantId tenantId, @Param("id") UUID id);

    @Query("SELECT d FROM ComplianceDocument d WHERE d.tenantId.value = :#{#tenantId.value} " +
           "AND d.registrationId = :registrationId ORDER BY d.documentType")
    List<ComplianceDocument> findByRegistration(@Param("tenantId") TenantId tenantId,
                                                @Param("registrationId") UUID registrationId);

    @Query("SELECT d FROM ComplianceDocument d WHERE d.tenantId.value = :#{#tenantId.value} ORDER BY d.expiryDate ASC NULLS LAST")
    List<ComplianceDocument> findAllForTenant(@Param("tenantId") TenantId tenantId);

    /** Cross-tenant sweep, same pattern as ComplianceRegistrationRepository's own. */
    @Query("SELECT d FROM ComplianceDocument d WHERE d.expiryDate IS NOT NULL AND d.expiryDate <= :cutoff")
    List<ComplianceDocument> findAllWithExpiryAcrossTenants(@Param("cutoff") LocalDate cutoff);
}
