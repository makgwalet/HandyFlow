package za.co.handyflow.platform.complianceservices.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.complianceservices.domain.model.ComplianceClientObligationHistory;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

public interface ComplianceClientObligationHistoryRepository extends JpaRepository<ComplianceClientObligationHistory, UUID> {

    @Query("SELECT h FROM ComplianceClientObligationHistory h WHERE h.tenantId.value = :#{#tenantId.value} AND h.assignmentId = :assignmentId ORDER BY h.occurredAt")
    List<ComplianceClientObligationHistory> findForAssignment(@Param("tenantId") TenantId tenantId, @Param("assignmentId") UUID assignmentId);
}
