package za.co.handyflow.platform.complianceservices.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.complianceservices.domain.model.*;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ComplianceCaseEventRepository extends JpaRepository<ComplianceCaseEvent, UUID> {
    @Query("SELECT e FROM ComplianceCaseEvent e WHERE e.tenantId.value = :#{#tenantId.value} AND e.caseId = :caseId ORDER BY e.occurredAt, e.id")
    List<ComplianceCaseEvent> findForCase(@Param("tenantId") TenantId tenantId, @Param("caseId") UUID caseId);
}
