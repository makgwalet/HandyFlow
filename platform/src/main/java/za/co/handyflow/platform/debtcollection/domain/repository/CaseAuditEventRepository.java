package za.co.handyflow.platform.debtcollection.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.debtcollection.domain.model.CaseAuditEvent;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

public interface CaseAuditEventRepository extends JpaRepository<CaseAuditEvent, UUID> {

    @Query("SELECT e FROM CaseAuditEvent e WHERE e.tenantId = :tenantId AND e.caseId = :caseId ORDER BY e.createdAt DESC")
    List<CaseAuditEvent> findByCaseId(TenantId tenantId, UUID caseId);
}
