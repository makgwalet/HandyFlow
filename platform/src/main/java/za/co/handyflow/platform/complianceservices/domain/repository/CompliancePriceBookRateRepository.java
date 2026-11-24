package za.co.handyflow.platform.complianceservices.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.complianceservices.domain.model.CompliancePriceBookRate;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

public interface CompliancePriceBookRateRepository extends JpaRepository<CompliancePriceBookRate, UUID> {

    @Query("SELECT r FROM CompliancePriceBookRate r WHERE r.tenantId.value = :#{#tenantId.value} AND r.versionId IN :versionIds ORDER BY r.versionId, r.sortOrder")
    List<CompliancePriceBookRate> findForVersions(@Param("tenantId") TenantId tenantId, @Param("versionIds") List<UUID> versionIds);

    @Modifying
    @Query("DELETE FROM CompliancePriceBookRate r WHERE r.versionId = :versionId")
    void deleteForVersion(@Param("versionId") UUID versionId);
}
