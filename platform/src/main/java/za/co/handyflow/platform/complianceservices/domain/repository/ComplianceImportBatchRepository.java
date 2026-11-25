package za.co.handyflow.platform.complianceservices.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.complianceservices.domain.model.ComplianceImportBatch;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ComplianceImportBatchRepository extends JpaRepository<ComplianceImportBatch, UUID> {

    @Query("SELECT b FROM ComplianceImportBatch b WHERE b.tenantId.value = :#{#tenantId.value} AND b.id = :id")
    Optional<ComplianceImportBatch> findOne(@Param("tenantId") TenantId tenantId, @Param("id") UUID id);

    @Query("SELECT b FROM ComplianceImportBatch b WHERE b.tenantId.value = :#{#tenantId.value} ORDER BY b.createdAt DESC")
    List<ComplianceImportBatch> findAllForTenant(@Param("tenantId") TenantId tenantId);
}
