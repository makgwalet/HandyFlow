package za.co.handyflow.platform.complianceservices.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.complianceservices.domain.model.ComplianceClientFieldSource;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ComplianceClientFieldSourceRepository extends JpaRepository<ComplianceClientFieldSource, UUID> {

    @Query("SELECT s FROM ComplianceClientFieldSource s WHERE s.tenantId.value = :#{#tenantId.value} AND s.clientId = :clientId ORDER BY s.fieldName")
    List<ComplianceClientFieldSource> findForClient(@Param("tenantId") TenantId tenantId, @Param("clientId") UUID clientId);

    @Query("SELECT s FROM ComplianceClientFieldSource s WHERE s.tenantId.value = :#{#tenantId.value} AND s.clientId = :clientId AND s.fieldName = :fieldName")
    Optional<ComplianceClientFieldSource> findForField(@Param("tenantId") TenantId tenantId, @Param("clientId") UUID clientId, @Param("fieldName") String fieldName);
}
