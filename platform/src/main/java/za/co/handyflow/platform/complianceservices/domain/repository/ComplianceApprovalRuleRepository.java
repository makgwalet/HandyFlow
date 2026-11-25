package za.co.handyflow.platform.complianceservices.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.complianceservices.domain.model.ComplianceApprovalRule;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ComplianceApprovalRuleRepository extends JpaRepository<ComplianceApprovalRule, UUID> {

    @Query("SELECT r FROM ComplianceApprovalRule r WHERE r.tenantId.value = :#{#tenantId.value} AND r.id = :id")
    Optional<ComplianceApprovalRule> findOne(@Param("tenantId") TenantId tenantId, @Param("id") UUID id);

    @Query("SELECT r FROM ComplianceApprovalRule r WHERE r.tenantId.value = :#{#tenantId.value} ORDER BY r.proposedAt DESC")
    List<ComplianceApprovalRule> findAllForTenant(@Param("tenantId") TenantId tenantId);

    @Query("SELECT r FROM ComplianceApprovalRule r WHERE r.tenantId.value = :#{#tenantId.value} AND r.status = 'ACTIVE'")
    List<ComplianceApprovalRule> findActive(@Param("tenantId") TenantId tenantId);
}
