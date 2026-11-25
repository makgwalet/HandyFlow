package za.co.handyflow.platform.complianceservices.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.complianceservices.domain.model.*;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ComplianceRegImpactRepository extends JpaRepository<ComplianceRegImpact, UUID> {
    @Query("SELECT i FROM ComplianceRegImpact i WHERE i.tenantId.value = :#{#tenantId.value} AND i.changeId = :changeId ORDER BY i.createdAt")
    List<ComplianceRegImpact> findForChange(@Param("tenantId") TenantId tenantId, @Param("changeId") UUID changeId);

    @Query("SELECT i FROM ComplianceRegImpact i WHERE i.tenantId.value = :#{#tenantId.value} AND i.clientId = :clientId ORDER BY i.createdAt DESC")
    List<ComplianceRegImpact> findForClient(@Param("tenantId") TenantId tenantId, @Param("clientId") UUID clientId);

    @Query("SELECT i FROM ComplianceRegImpact i WHERE i.tenantId.value = :#{#tenantId.value} AND i.id = :id")
    Optional<ComplianceRegImpact> findOne(@Param("tenantId") TenantId tenantId, @Param("id") UUID id);

    @Query("SELECT i FROM ComplianceRegImpact i WHERE i.tenantId.value = :#{#tenantId.value} AND i.status IN ('OPEN','NOTICE_GIVEN')")
    List<ComplianceRegImpact> findLive(@Param("tenantId") TenantId tenantId);

    @Query("SELECT i FROM ComplianceRegImpact i WHERE i.tenantId.value = :#{#tenantId.value}")
    List<ComplianceRegImpact> findAllForTenant(@Param("tenantId") TenantId tenantId);
}
