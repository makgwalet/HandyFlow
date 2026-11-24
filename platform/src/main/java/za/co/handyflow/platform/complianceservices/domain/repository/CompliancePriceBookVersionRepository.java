package za.co.handyflow.platform.complianceservices.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.complianceservices.domain.model.CompliancePriceBookVersion;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CompliancePriceBookVersionRepository extends JpaRepository<CompliancePriceBookVersion, UUID> {

    @Query("SELECT v FROM CompliancePriceBookVersion v WHERE v.tenantId.value = :#{#tenantId.value} ORDER BY v.versionNumber")
    List<CompliancePriceBookVersion> findAllForTenant(@Param("tenantId") TenantId tenantId);

    @Query("SELECT v FROM CompliancePriceBookVersion v WHERE v.tenantId.value = :#{#tenantId.value} AND v.id = :id")
    Optional<CompliancePriceBookVersion> findOne(@Param("tenantId") TenantId tenantId, @Param("id") UUID id);
}
