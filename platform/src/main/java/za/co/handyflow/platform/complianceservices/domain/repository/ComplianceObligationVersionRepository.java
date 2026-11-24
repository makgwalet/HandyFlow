package za.co.handyflow.platform.complianceservices.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.complianceservices.domain.model.ComplianceObligationVersion;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ComplianceObligationVersionRepository extends JpaRepository<ComplianceObligationVersion, UUID> {

    @Query("SELECT v FROM ComplianceObligationVersion v WHERE v.tenantId.value = :#{#tenantId.value} ORDER BY v.obligationId, v.versionNumber")
    List<ComplianceObligationVersion> findAllForTenant(@Param("tenantId") TenantId tenantId);

    @Query("SELECT v FROM ComplianceObligationVersion v WHERE v.tenantId.value = :#{#tenantId.value} AND v.obligationId = :obligationId ORDER BY v.versionNumber")
    List<ComplianceObligationVersion> findForObligation(@Param("tenantId") TenantId tenantId, @Param("obligationId") UUID obligationId);

    @Query("SELECT v FROM ComplianceObligationVersion v WHERE v.tenantId.value = :#{#tenantId.value} AND v.obligationId = :obligationId AND v.id = :id")
    Optional<ComplianceObligationVersion> findOne(@Param("tenantId") TenantId tenantId, @Param("obligationId") UUID obligationId, @Param("id") UUID id);
}
