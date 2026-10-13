package za.co.handyflow.platform.debtcollection.domain.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import za.co.handyflow.platform.debtcollection.domain.model.PtpEvent;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/** Read-only feed of PTP events by case for the unified timeline (PtpEventRepository only looks up by promise). */
public interface CaseTimelineRepository extends Repository<PtpEvent, UUID> {

    @Query("SELECT e FROM PtpEvent e WHERE e.tenantId = :tenantId AND e.caseId = :caseId ORDER BY e.createdAt DESC")
    List<PtpEvent> findPtpEventsByCaseId(TenantId tenantId, UUID caseId);
}
