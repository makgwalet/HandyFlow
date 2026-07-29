package za.co.handyflow.platform.tasks.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.tasks.domain.model.Task;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaskRepository extends JpaRepository<Task, UUID> {

    List<Task> findByBoardIdAndDeletedAtIsNullOrderBySortOrderAscCreatedAtAsc(UUID boardId);

    List<Task> findByColumnIdAndDeletedAtIsNullOrderBySortOrderAsc(UUID columnId);

    Optional<Task> findByIdAndTenantId(UUID id, TenantId tenantId);

    @Query("""
        SELECT t FROM Task t
        WHERE t.tenantId = :tenantId AND t.assigneeId = :userId
        AND t.status NOT IN ('DONE','CANCELLED') AND t.deletedAt IS NULL
        ORDER BY t.dueDate ASC NULLS LAST, t.priority DESC
        """)
    List<Task> findMyTasks(TenantId tenantId, UUID userId);

    @Query("""
        SELECT t FROM Task t
        WHERE t.tenantId = :tenantId AND t.dueDate < :today
        AND t.status NOT IN ('DONE','CANCELLED') AND t.deletedAt IS NULL
        """)
    List<Task> findOverdue(TenantId tenantId, LocalDate today);

    @Query("""
        SELECT t FROM Task t
        WHERE t.tenantId = :tenantId
        AND t.linkedEntityType = :entityType AND t.linkedEntityId = :entityId
        AND t.deletedAt IS NULL ORDER BY t.createdAt DESC
        """)
    List<Task> findByLinkedEntity(TenantId tenantId, String entityType, UUID entityId);

    @Query("""
        SELECT COUNT(t) FROM Task t
        WHERE t.tenantId = :tenantId AND t.status = :status AND t.deletedAt IS NULL
        """)
    long countByStatus(TenantId tenantId, String status);

    /**
     * FIX: replaces getSummary() calling findOverdue().size() which loaded all tasks into memory.
     */
    @Query("""
        SELECT COUNT(t) FROM Task t
        WHERE t.tenantId = :tenantId AND t.dueDate < :today
        AND t.status NOT IN ('DONE','CANCELLED') AND t.deletedAt IS NULL
        """)
    long countOverdue(TenantId tenantId, LocalDate today);

    /**
     * FIX: replaces getSummary() calling findMyTasks().size() which loaded all tasks into memory.
     */
    @Query("""
        SELECT COUNT(t) FROM Task t
        WHERE t.tenantId = :tenantId AND t.assigneeId = :userId
        AND t.status NOT IN ('DONE','CANCELLED') AND t.deletedAt IS NULL
        """)
    long countMyTasks(TenantId tenantId, UUID userId);

    // ── Notification scheduler (cross-tenant sweeps) ────────────────────────

    /**
     * Assigned, undone tasks due on an exact target date — used by the
     * TASK_DUE_SOON reminder sweep. Exact-day match (same idempotency style
     * as Fleet's compliance alerts) means a task only fires once per
     * configured lead time, without needing its own "reminder sent" flag.
     */
    @Query("""
        SELECT t FROM Task t
        WHERE t.dueDate = :targetDate AND t.assigneeId IS NOT NULL
        AND t.status NOT IN ('DONE','CANCELLED') AND t.deletedAt IS NULL
        """)
    List<Task> findDueOnDateAcrossTenants(LocalDate targetDate);

    /**
     * Assigned, undone, overdue tasks that haven't been alerted yet — used by
     * the TASK_OVERDUE sweep. overdueAlertSentAt is the idempotency guard so
     * a task overdue for a week doesn't re-notify every single day.
     */
    @Query("""
        SELECT t FROM Task t
        WHERE t.dueDate < :today AND t.assigneeId IS NOT NULL
        AND t.status NOT IN ('DONE','CANCELLED') AND t.deletedAt IS NULL
        AND t.overdueAlertSentAt IS NULL
        """)
    List<Task> findOverdueNeedingAlertAcrossTenants(LocalDate today);
}