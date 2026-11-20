package za.co.handyflow.platform.legalpractice.domain.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.legalpractice.domain.model.LpLitigationEvent;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

public interface LpLitigationEventRepository extends JpaRepository<LpLitigationEvent, UUID> {

    @Query("SELECT e FROM LpLitigationEvent e WHERE e.tenantId = :tenantId AND e.caseId = :caseId ORDER BY e.occurredAt DESC")
    List<LpLitigationEvent> findRecentForCase(TenantId tenantId, UUID caseId, Pageable pageable);
}
