package za.co.handyflow.platform.complianceservices.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.complianceservices.domain.model.*;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ComplianceCaseStepRepository extends JpaRepository<ComplianceCaseStep, UUID> {
    @Query("SELECT s FROM ComplianceCaseStep s WHERE s.tenantId.value = :#{#tenantId.value} AND s.caseId IN :caseIds ORDER BY s.caseId, s.sortOrder")
    List<ComplianceCaseStep> findForCases(@Param("tenantId") TenantId tenantId, @Param("caseIds") List<UUID> caseIds);

    @Query("SELECT s FROM ComplianceCaseStep s WHERE s.tenantId.value = :#{#tenantId.value} AND s.caseId = :caseId AND s.id = :id")
    Optional<ComplianceCaseStep> findOne(@Param("tenantId") TenantId tenantId, @Param("caseId") UUID caseId, @Param("id") UUID id);
}
