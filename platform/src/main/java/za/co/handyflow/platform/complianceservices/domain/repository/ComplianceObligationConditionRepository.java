package za.co.handyflow.platform.complianceservices.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.complianceservices.domain.model.ComplianceObligationCondition;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

public interface ComplianceObligationConditionRepository extends JpaRepository<ComplianceObligationCondition, UUID> {

    @Query("SELECT c FROM ComplianceObligationCondition c WHERE c.tenantId.value = :#{#tenantId.value} AND c.versionId IN :versionIds ORDER BY c.versionId, c.sortOrder")
    List<ComplianceObligationCondition> findForVersions(@Param("tenantId") TenantId tenantId, @Param("versionIds") List<UUID> versionIds);

    @Modifying
    @Query("DELETE FROM ComplianceObligationCondition c WHERE c.versionId = :versionId")
    void deleteForVersion(@Param("versionId") UUID versionId);
}
