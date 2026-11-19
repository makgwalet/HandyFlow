package za.co.handyflow.platform.legalcompliance.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.legalcompliance.domain.model.*;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PolicyVersionRepository extends JpaRepository<PolicyVersion, UUID> {

    @Query("SELECT v FROM PolicyVersion v WHERE v.tenantId = :tenantId AND v.id = :id")
    Optional<PolicyVersion> findForTenant(TenantId tenantId, UUID id);

    @Query("SELECT v FROM PolicyVersion v WHERE v.tenantId = :tenantId AND v.policyId = :policyId ORDER BY v.versionNumber DESC")
    List<PolicyVersion> findByPolicy(TenantId tenantId, UUID policyId);

    @Query("SELECT v FROM PolicyVersion v WHERE v.tenantId = :tenantId AND v.policyId = :policyId AND v.status = za.co.handyflow.platform.legalcompliance.domain.model.PolicyVersionStatus.PUBLISHED")
    Optional<PolicyVersion> findPublished(TenantId tenantId, UUID policyId);

    @Query("SELECT v FROM PolicyVersion v WHERE v.tenantId = :tenantId AND v.policyId = :policyId AND v.status IN (za.co.handyflow.platform.legalcompliance.domain.model.PolicyVersionStatus.DRAFT, za.co.handyflow.platform.legalcompliance.domain.model.PolicyVersionStatus.IN_REVIEW, za.co.handyflow.platform.legalcompliance.domain.model.PolicyVersionStatus.APPROVED)")
    Optional<PolicyVersion> findOpen(TenantId tenantId, UUID policyId);

    @Query("SELECT COALESCE(MAX(v.versionNumber), 0) FROM PolicyVersion v WHERE v.tenantId = :tenantId AND v.policyId = :policyId")
    int maxVersion(TenantId tenantId, UUID policyId);

}
