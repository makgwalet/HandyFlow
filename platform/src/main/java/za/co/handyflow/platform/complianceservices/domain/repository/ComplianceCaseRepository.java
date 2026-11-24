package za.co.handyflow.platform.complianceservices.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.complianceservices.domain.model.*;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ComplianceCaseRepository extends JpaRepository<ComplianceCase, UUID> {
    @Query("SELECT c FROM ComplianceCase c WHERE c.tenantId.value = :#{#tenantId.value} AND c.id = :id")
    Optional<ComplianceCase> findOne(@Param("tenantId") TenantId tenantId, @Param("id") UUID id);

    @Query("SELECT c FROM ComplianceCase c WHERE c.tenantId.value = :#{#tenantId.value} AND c.clientId = :clientId ORDER BY c.createdAt DESC")
    List<ComplianceCase> findForClient(@Param("tenantId") TenantId tenantId, @Param("clientId") UUID clientId);

    @Query("SELECT c FROM ComplianceCase c WHERE c.tenantId.value = :#{#tenantId.value} AND c.clientId = :clientId AND c.obligationId = :obligationId AND c.status IN ('OPEN','IN_PROGRESS','AWAITING_CLIENT','RESOLVED')")
    Optional<ComplianceCase> findLive(@Param("tenantId") TenantId tenantId, @Param("clientId") UUID clientId, @Param("obligationId") UUID obligationId);

    @Query("SELECT c FROM ComplianceCase c WHERE c.tenantId.value = :#{#tenantId.value} AND c.status IN ('OPEN','IN_PROGRESS','AWAITING_CLIENT')")
    List<ComplianceCase> findLiveForTenant(@Param("tenantId") TenantId tenantId);
}
