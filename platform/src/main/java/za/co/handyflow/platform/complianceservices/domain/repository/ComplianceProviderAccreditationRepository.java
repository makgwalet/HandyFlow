package za.co.handyflow.platform.complianceservices.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.complianceservices.domain.model.ComplianceProviderAccreditation;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ComplianceProviderAccreditationRepository extends JpaRepository<ComplianceProviderAccreditation, UUID> {

    @Query("SELECT a FROM ComplianceProviderAccreditation a WHERE a.tenantId.value = :#{#tenantId.value} AND a.id = :id")
    Optional<ComplianceProviderAccreditation> findOne(@Param("tenantId") TenantId tenantId, @Param("id") UUID id);

    @Query("SELECT a FROM ComplianceProviderAccreditation a WHERE a.tenantId.value = :#{#tenantId.value} AND a.providerId = :providerId ORDER BY a.recordedAt DESC")
    List<ComplianceProviderAccreditation> findForProvider(@Param("tenantId") TenantId tenantId, @Param("providerId") UUID providerId);

    /** Accreditations still in play (claimed or checked) for providers that are active: what the work queue watches. */
    @Query("SELECT a FROM ComplianceProviderAccreditation a, ComplianceProvider p WHERE a.tenantId.value = :#{#tenantId.value} AND p.id = a.providerId AND p.status = 'ACTIVE' AND a.status IN ('CLAIMED', 'VERIFIED')")
    List<ComplianceProviderAccreditation> findLiveForActiveProviders(@Param("tenantId") TenantId tenantId);
}
