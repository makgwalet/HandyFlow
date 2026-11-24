package za.co.handyflow.platform.complianceservices.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.complianceservices.domain.model.ComplianceObligationEvidence;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

public interface ComplianceObligationEvidenceRepository extends JpaRepository<ComplianceObligationEvidence, UUID> {

    @Query("SELECT e FROM ComplianceObligationEvidence e WHERE e.tenantId.value = :#{#tenantId.value} AND e.versionId IN :versionIds ORDER BY e.versionId, e.sortOrder")
    List<ComplianceObligationEvidence> findForVersions(@Param("tenantId") TenantId tenantId, @Param("versionIds") List<UUID> versionIds);

    @Modifying
    @Query("DELETE FROM ComplianceObligationEvidence e WHERE e.versionId = :versionId")
    void deleteForVersion(@Param("versionId") UUID versionId);
}
