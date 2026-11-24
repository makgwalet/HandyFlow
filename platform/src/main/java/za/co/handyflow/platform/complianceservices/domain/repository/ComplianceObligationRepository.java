package za.co.handyflow.platform.complianceservices.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.complianceservices.domain.model.ComplianceObligation;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ComplianceObligationRepository extends JpaRepository<ComplianceObligation, UUID> {

    @Query("SELECT o FROM ComplianceObligation o WHERE o.tenantId.value = :#{#tenantId.value} ORDER BY o.code")
    List<ComplianceObligation> findAllForTenant(@Param("tenantId") TenantId tenantId);

    @Query("SELECT o FROM ComplianceObligation o WHERE o.tenantId.value = :#{#tenantId.value} AND o.id = :id")
    Optional<ComplianceObligation> findByIdForTenant(@Param("tenantId") TenantId tenantId, @Param("id") UUID id);

    @Query("SELECT o FROM ComplianceObligation o WHERE o.tenantId.value = :#{#tenantId.value} AND o.code = :code")
    Optional<ComplianceObligation> findByCode(@Param("tenantId") TenantId tenantId, @Param("code") String code);
}
