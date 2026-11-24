package za.co.handyflow.platform.complianceservices.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.complianceservices.domain.model.ComplianceServiceDefinition;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ComplianceServiceDefinitionRepository extends JpaRepository<ComplianceServiceDefinition, UUID> {

    @Query("SELECT o FROM ComplianceServiceDefinition o WHERE o.tenantId.value = :#{#tenantId.value} ORDER BY o.code")
    List<ComplianceServiceDefinition> findAllForTenant(@Param("tenantId") TenantId tenantId);

    @Query("SELECT o FROM ComplianceServiceDefinition o WHERE o.tenantId.value = :#{#tenantId.value} AND o.id = :id")
    Optional<ComplianceServiceDefinition> findByIdForTenant(@Param("tenantId") TenantId tenantId, @Param("id") UUID id);

    @Query("SELECT o FROM ComplianceServiceDefinition o WHERE o.tenantId.value = :#{#tenantId.value} AND o.code = :code")
    Optional<ComplianceServiceDefinition> findByCode(@Param("tenantId") TenantId tenantId, @Param("code") String code);
}
