package za.co.handyflow.platform.complianceservices.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.complianceservices.domain.model.*;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ComplianceAssessmentRepository extends JpaRepository<ComplianceAssessment, UUID> {
    @Query("SELECT a FROM ComplianceAssessment a WHERE a.tenantId.value = :#{#tenantId.value} AND a.clientId = :clientId ORDER BY a.startedAt DESC")
    List<ComplianceAssessment> findForClient(@Param("tenantId") TenantId tenantId, @Param("clientId") UUID clientId);

    @Query("SELECT a FROM ComplianceAssessment a WHERE a.tenantId.value = :#{#tenantId.value} AND a.clientId = :clientId AND a.id = :id")
    Optional<ComplianceAssessment> findOne(@Param("tenantId") TenantId tenantId, @Param("clientId") UUID clientId, @Param("id") UUID id);

    @Query("SELECT a FROM ComplianceAssessment a WHERE a.tenantId.value = :#{#tenantId.value} AND a.clientId = :clientId AND a.status = 'DRAFT'")
    Optional<ComplianceAssessment> findDraft(@Param("tenantId") TenantId tenantId, @Param("clientId") UUID clientId);

    @Query("SELECT a FROM ComplianceAssessment a WHERE a.tenantId.value = :#{#tenantId.value} AND a.status = 'DRAFT'")
    List<ComplianceAssessment> findDraftsForTenant(@Param("tenantId") TenantId tenantId);
}
