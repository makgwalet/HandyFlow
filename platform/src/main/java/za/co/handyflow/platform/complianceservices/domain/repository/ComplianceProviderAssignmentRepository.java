package za.co.handyflow.platform.complianceservices.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.complianceservices.domain.model.ComplianceProviderAssignment;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ComplianceProviderAssignmentRepository extends JpaRepository<ComplianceProviderAssignment, UUID> {

    @Query("SELECT a FROM ComplianceProviderAssignment a WHERE a.tenantId.value = :#{#tenantId.value} AND a.id = :id")
    Optional<ComplianceProviderAssignment> findOne(@Param("tenantId") TenantId tenantId, @Param("id") UUID id);

    @Query("SELECT a FROM ComplianceProviderAssignment a WHERE a.tenantId.value = :#{#tenantId.value} ORDER BY a.createdAt DESC")
    List<ComplianceProviderAssignment> findAllForTenant(@Param("tenantId") TenantId tenantId);

    @Query("SELECT a FROM ComplianceProviderAssignment a WHERE a.tenantId.value = :#{#tenantId.value} AND a.clientId = :clientId ORDER BY a.createdAt DESC")
    List<ComplianceProviderAssignment> findForClient(@Param("tenantId") TenantId tenantId, @Param("clientId") UUID clientId);

    @Query("SELECT a FROM ComplianceProviderAssignment a WHERE a.tenantId.value = :#{#tenantId.value} AND a.providerId = :providerId ORDER BY a.createdAt DESC")
    List<ComplianceProviderAssignment> findForProvider(@Param("tenantId") TenantId tenantId, @Param("providerId") UUID providerId);

    /** Proposed and started work: what the work queue watches. */
    @Query("SELECT a FROM ComplianceProviderAssignment a WHERE a.tenantId.value = :#{#tenantId.value} AND a.status IN ('PROPOSED', 'ACTIVE')")
    List<ComplianceProviderAssignment> findOpen(@Param("tenantId") TenantId tenantId);
}
