package za.co.handyflow.platform.complianceservices.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.complianceservices.domain.model.CompliancePriceBookLine;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

public interface CompliancePriceBookLineRepository extends JpaRepository<CompliancePriceBookLine, UUID> {

    @Query("SELECT l FROM CompliancePriceBookLine l WHERE l.tenantId.value = :#{#tenantId.value} AND l.versionId IN :versionIds ORDER BY l.versionId, l.sortOrder")
    List<CompliancePriceBookLine> findForVersions(@Param("tenantId") TenantId tenantId, @Param("versionIds") List<UUID> versionIds);

    @Modifying
    @Query("DELETE FROM CompliancePriceBookLine l WHERE l.versionId = :versionId")
    void deleteForVersion(@Param("versionId") UUID versionId);
}
