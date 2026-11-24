package za.co.handyflow.platform.complianceservices.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.complianceservices.domain.model.ComplianceClientSite;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ComplianceClientSiteRepository extends JpaRepository<ComplianceClientSite, UUID> {

    @Query("SELECT s FROM ComplianceClientSite s WHERE s.tenantId.value = :#{#tenantId.value} AND s.clientId = :clientId ORDER BY s.name")
    List<ComplianceClientSite> findForClient(@Param("tenantId") TenantId tenantId, @Param("clientId") UUID clientId);

    @Query("SELECT s FROM ComplianceClientSite s WHERE s.tenantId.value = :#{#tenantId.value} AND s.clientId = :clientId AND s.id = :id")
    Optional<ComplianceClientSite> findByIdForClient(@Param("tenantId") TenantId tenantId, @Param("clientId") UUID clientId, @Param("id") UUID id);
}
