package za.co.handyflow.platform.complianceservices.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.complianceservices.domain.model.ComplianceImportRow;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

public interface ComplianceImportRowRepository extends JpaRepository<ComplianceImportRow, UUID> {

    @Query("SELECT r FROM ComplianceImportRow r WHERE r.tenantId.value = :#{#tenantId.value} AND r.batchId = :batchId ORDER BY r.rowNumber")
    List<ComplianceImportRow> findForBatch(@Param("tenantId") TenantId tenantId, @Param("batchId") UUID batchId);
}
