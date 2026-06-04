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
}
