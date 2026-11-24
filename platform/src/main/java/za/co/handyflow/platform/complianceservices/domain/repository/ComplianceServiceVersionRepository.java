package za.co.handyflow.platform.complianceservices.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.complianceservices.domain.model.ComplianceServiceVersion;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ComplianceServiceVersionRepository extends JpaRepository<ComplianceServiceVersion, UUID> {

    @Query("SELECT v FROM ComplianceServiceVersion v WHERE v.tenantId.value = :#{#tenantId.value} ORDER BY v.serviceId, v.versionNumber")
    List<ComplianceServiceVersion> findAllForTenant(@Param("tenantId") TenantId tenantId);

    @Query("SELECT v FROM ComplianceServiceVersion v WHERE v.tenantId.value = :#{#tenantId.value} AND v.serviceId = :serviceId ORDER BY v.versionNumber")
    List<ComplianceServiceVersion> findForService(@Param("tenantId") TenantId tenantId, @Param("serviceId") UUID serviceId);

    @Query("SELECT v FROM ComplianceServiceVersion v WHERE v.tenantId.value = :#{#tenantId.value} AND v.serviceId = :serviceId AND v.id = :id")
    Optional<ComplianceServiceVersion> findOne(@Param("tenantId") TenantId tenantId, @Param("serviceId") UUID serviceId, @Param("id") UUID id);

    @Query("SELECT v FROM ComplianceServiceVersion v WHERE v.tenantId.value = :#{#tenantId.value} AND v.reviewStatus = 'IN_REVIEW'")
    List<ComplianceServiceVersion> findInReview(@Param("tenantId") TenantId tenantId);
}
