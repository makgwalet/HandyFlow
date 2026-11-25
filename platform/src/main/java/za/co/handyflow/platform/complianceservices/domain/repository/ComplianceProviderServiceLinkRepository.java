package za.co.handyflow.platform.complianceservices.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.complianceservices.domain.model.ComplianceProviderServiceLink;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

public interface ComplianceProviderServiceLinkRepository extends JpaRepository<ComplianceProviderServiceLink, UUID> {

    @Query("SELECT l FROM ComplianceProviderServiceLink l WHERE l.tenantId.value = :#{#tenantId.value} AND l.providerId = :providerId ORDER BY l.serviceCode")
    List<ComplianceProviderServiceLink> findForProvider(@Param("tenantId") TenantId tenantId, @Param("providerId") UUID providerId);

    @Query("SELECT l FROM ComplianceProviderServiceLink l WHERE l.tenantId.value = :#{#tenantId.value}")
    List<ComplianceProviderServiceLink> findAllForTenant(@Param("tenantId") TenantId tenantId);
}
