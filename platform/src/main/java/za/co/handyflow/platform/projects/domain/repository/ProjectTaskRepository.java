package za.co.handyflow.platform.projects.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.projects.domain.model.ProjectTask;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectTaskRepository extends JpaRepository<ProjectTask, UUID> {

    @Query("""
            SELECT t FROM ProjectTask t
            WHERE t.projectId = :projectId AND t.parentTaskId IS NULL
            ORDER BY t.sortOrder, t.plannedStart
            """)
    List<ProjectTask> findRootTasks(@Param("projectId") UUID projectId);

    @Query("""
            SELECT t FROM ProjectTask t
            WHERE t.projectId = :projectId
            ORDER BY t.sortOrder, t.plannedStart
            """)
    List<ProjectTask> findByProject(@Param("projectId") UUID projectId);

    @Query("SELECT t FROM ProjectTask t WHERE t.phaseId = :phaseId ORDER BY t.sortOrder")
    List<ProjectTask> findByPhase(@Param("phaseId") UUID phaseId);

    @Query("""
            SELECT t FROM ProjectTask t
            WHERE t.tenantId   = :tenantId
              AND t.assigneeId = :assigneeId
              AND t.status NOT IN ('COMPLETED','CANCELLED')
            ORDER BY t.plannedEnd
            """)
    List<ProjectTask> findOpenByAssignee(@Param("tenantId")   UUID tenantId,
                                         @Param("assigneeId") UUID assigneeId);

    @Query("""
            SELECT t FROM ProjectTask t
            WHERE t.projectId  = :projectId
              AND t.isCritical = true
            ORDER BY t.plannedStart
            """)
    List<ProjectTask> findCriticalPath(@Param("projectId") UUID projectId);

    @Query("""
            SELECT t FROM ProjectTask t
            WHERE t.projectId   = :projectId
              AND t.isMilestone = true
            ORDER BY t.plannedEnd
            """)
    List<ProjectTask> findMilestones(@Param("projectId") UUID projectId);

    @Query("SELECT t FROM ProjectTask t WHERE t.tenantId = :tenantId AND t.id = :id")
    Optional<ProjectTask> findByTenantAndId(@Param("tenantId") UUID tenantId,
                                            @Param("id")       UUID id);

    // ── Aggregate queries (DB-side, not Java-stream) ─────────────────────────

    /**
     * Used by recalculateHealth() in ProjectService.
     *
     * WHY THIS MATTERS:
     * The original code called findByProject() — fetching ALL task entities into
     * the JVM — just to sum one BigDecimal column.  For a project with 200 tasks
     * that's 200 row hydrations for a single addition.  A SQL SUM() does this in
     * the database in microseconds with no heap allocation.
     */
    @Query("""
            SELECT COALESCE(SUM(t.actualCost), 0)
            FROM ProjectTask t
            WHERE t.projectId = :projectId
            """)
    BigDecimal sumActualCostByProject(@Param("projectId") UUID projectId);

    /**
     * Returns the next task sort-order integer.
     * Kept here for reference but SequenceService.nextTaskNumber() is now
     * the canonical way to get the sequence — this is used only for sort_order
     * which does NOT need to be globally unique, just monotonically increasing.
     */
    @Query("""
            SELECT COALESCE(MAX(t.sortOrder), 0)
            FROM ProjectTask t
            WHERE t.projectId = :projectId
            """)
    int findMaxSortOrder(@Param("projectId") UUID projectId);

    /**
     * Returns milestone tasks that are past their planned end date and not yet completed.
     * Result columns: [tenantId, projectName, taskTitle, plannedEnd]
     */
    @Query(value = """
            SELECT t.tenant_id, p.name, t.title, t.planned_end
            FROM project_tasks t
            JOIN projects p ON p.id = t.project_id
            WHERE t.is_milestone = true
              AND t.status      != 'COMPLETED'
              AND t.status      != 'CANCELLED'
              AND t.planned_end  < :today
              AND p.status   IN ('ACTIVE', 'ON_HOLD')
            ORDER BY t.tenant_id, t.planned_end
            """, nativeQuery = true)
    List<Object[]> findOverdueMilestones(@Param("today") LocalDate today);
}
