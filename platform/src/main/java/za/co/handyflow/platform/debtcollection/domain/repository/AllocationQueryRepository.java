package za.co.handyflow.platform.debtcollection.domain.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import za.co.handyflow.platform.debtcollection.domain.model.WorkItem;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/** Read-only work-item lookups the allocator needs (the Chunk 1 repository has no by-case or by-tenant live query). */
public interface AllocationQueryRepository extends Repository<WorkItem, UUID> {

    @Query("SELECT w FROM WorkItem w WHERE w.tenantId = :tenantId AND w.caseId = :caseId AND w.status IN ('OPEN','SNOOZED')")
    List<WorkItem> findLiveByCaseId(TenantId tenantId, UUID caseId);

    @Query("SELECT w FROM WorkItem w WHERE w.tenantId = :tenantId AND w.status IN ('OPEN','SNOOZED')")
    List<WorkItem> findLiveByTenant(TenantId tenantId);
}
