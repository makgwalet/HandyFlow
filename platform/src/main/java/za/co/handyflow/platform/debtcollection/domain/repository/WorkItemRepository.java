package za.co.handyflow.platform.debtcollection.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.debtcollection.domain.model.WorkItem;
import za.co.handyflow.platform.debtcollection.domain.model.WorkItemStatus;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkItemRepository extends JpaRepository<WorkItem, UUID> {

    @Query("SELECT w FROM WorkItem w WHERE w.tenantId = :tenantId AND w.id = :id")
    Optional<WorkItem> findByTenantIdAndId(TenantId tenantId, UUID id);

    /** Ranked queue: highest effective priority first, then earliest due date (nulls last). Only OPEN (actionable) items. */
    @Query("""
        SELECT w FROM WorkItem w WHERE w.tenantId = :tenantId AND w.status = 'OPEN'
        AND w.assignedToUserId = :userId
        ORDER BY COALESCE(w.priorityOverride, w.priorityScore) DESC, w.dueDate ASC NULLS LAST, w.createdAt ASC
        """)
    Page<WorkItem> findMyQueue(TenantId tenantId, UUID userId, Pageable pageable);

    /** Supervisor view across the team; both filters optional. */
    @Query("""
        SELECT w FROM WorkItem w WHERE w.tenantId = :tenantId
        AND (:status IS NULL OR w.status = :status)
        AND (:assigneeId IS NULL OR w.assignedToUserId = :assigneeId)
        ORDER BY COALESCE(w.priorityOverride, w.priorityScore) DESC, w.dueDate ASC NULLS LAST, w.createdAt ASC
        """)
    Page<WorkItem> findTeamQueue(TenantId tenantId, WorkItemStatus status, UUID assigneeId, Pageable pageable);

    @Query("SELECT w FROM WorkItem w WHERE w.tenantId = :tenantId AND w.status = 'OPEN' AND w.assignedToUserId = :userId")
    List<WorkItem> findOpenForUser(TenantId tenantId, UUID userId);

    /** Dedup lookup — matches ANY status so a completed item is never re-raised by the same trigger. */
    @Query("SELECT w FROM WorkItem w WHERE w.tenantId = :tenantId AND w.sourceKey = :sourceKey")
    Optional<WorkItem> findBySourceKey(TenantId tenantId, String sourceKey);

    /** Cross-tenant daily sweeps. */
    @Query("SELECT w FROM WorkItem w WHERE w.status IN ('OPEN','SNOOZED')")
    List<WorkItem> findAllLiveAcrossTenants();

    @Query("SELECT w FROM WorkItem w WHERE w.status = 'SNOOZED' AND w.snoozedUntil <= :today")
    List<WorkItem> findSnoozeExpiredAcrossTenants(LocalDate today);
}
