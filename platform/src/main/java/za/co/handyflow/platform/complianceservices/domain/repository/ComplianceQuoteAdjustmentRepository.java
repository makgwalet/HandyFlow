package za.co.handyflow.platform.complianceservices.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.complianceservices.domain.model.ComplianceQuoteAdjustment;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

public interface ComplianceQuoteAdjustmentRepository extends JpaRepository<ComplianceQuoteAdjustment, UUID> {

    @Query("SELECT a FROM ComplianceQuoteAdjustment a WHERE a.tenantId.value = :#{#tenantId.value} AND a.lineId IN :lineIds ORDER BY a.lineId, a.sortOrder")
    List<ComplianceQuoteAdjustment> findForLines(@Param("tenantId") TenantId tenantId, @Param("lineIds") List<UUID> lineIds);
}
