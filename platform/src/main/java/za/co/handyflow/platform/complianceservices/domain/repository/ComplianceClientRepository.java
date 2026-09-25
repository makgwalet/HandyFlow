package za.co.handyflow.platform.complianceservices.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.complianceservices.domain.model.ComplianceClient;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;
import java.util.UUID;

public interface ComplianceClientRepository extends JpaRepository<ComplianceClient, UUID> {

    @Query("SELECT c FROM ComplianceClient c WHERE c.tenantId.value = :#{#tenantId.value} AND c.id = :id")
    Optional<ComplianceClient> findByIdForTenant(@Param("tenantId") TenantId tenantId, @Param("id") UUID id);

    @Query("SELECT c FROM ComplianceClient c WHERE c.tenantId.value = :#{#tenantId.value} " +
           "AND (:status IS NULL OR c.status = :status) ORDER BY c.name")
    Page<ComplianceClient> findAll(@Param("tenantId") TenantId tenantId, @Param("status") String status, Pageable pageable);

    @Query("SELECT c FROM ComplianceClient c WHERE c.tenantId.value = :#{#tenantId.value} AND c.crmCustomerId = :crmCustomerId")
    Optional<ComplianceClient> findByCrmCustomerId(@Param("tenantId") TenantId tenantId, @Param("crmCustomerId") UUID crmCustomerId);
}
