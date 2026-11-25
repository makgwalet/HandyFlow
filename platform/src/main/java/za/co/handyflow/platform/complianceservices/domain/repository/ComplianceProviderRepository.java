package za.co.handyflow.platform.complianceservices.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.complianceservices.domain.model.ComplianceProvider;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ComplianceProviderRepository extends JpaRepository<ComplianceProvider, UUID> {

    @Query("SELECT p FROM ComplianceProvider p WHERE p.tenantId.value = :#{#tenantId.value} AND p.id = :id")
    Optional<ComplianceProvider> findOne(@Param("tenantId") TenantId tenantId, @Param("id") UUID id);

    @Query("SELECT p FROM ComplianceProvider p WHERE p.tenantId.value = :#{#tenantId.value} ORDER BY p.status, lower(p.name)")
    List<ComplianceProvider> findAllForTenant(@Param("tenantId") TenantId tenantId);

    @Query("SELECT p FROM ComplianceProvider p WHERE p.tenantId.value = :#{#tenantId.value} AND lower(p.email) = lower(:email)")
    Optional<ComplianceProvider> findByEmail(@Param("tenantId") TenantId tenantId, @Param("email") String email);
}
