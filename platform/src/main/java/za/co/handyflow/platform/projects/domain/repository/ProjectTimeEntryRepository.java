package za.co.handyflow.platform.projects.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.projects.domain.model.TimeEntry;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectTimeEntryRepository extends JpaRepository<TimeEntry, UUID> {

    @Query("""
            SELECT t FROM TimeEntry t
            WHERE t.projectId = :projectId
            ORDER BY t.entryDate DESC
            """)
    List<TimeEntry> findByProject(@Param("projectId") UUID projectId);

    @Query("""
            SELECT t FROM TimeEntry t
            WHERE t.tenantId   = :tenantId
              AND t.userId     = :userId
              AND t.entryDate BETWEEN :from AND :to
            ORDER BY t.entryDate DESC
            """)
    List<TimeEntry> findByUserAndPeriod(@Param("tenantId") UUID tenantId,
                                        @Param("userId")   UUID userId,
                                        @Param("from")     LocalDate from,
                                        @Param("to")       LocalDate to);

    @Query("""
            SELECT t FROM TimeEntry t
            WHERE t.tenantId = :tenantId
              AND t.status   = 'SUBMITTED'
            ORDER BY t.entryDate
            """)
    List<TimeEntry> findPendingApproval(@Param("tenantId") UUID tenantId);

    /**
     * COUNT variant — used by getSummary() dashboard KPIs.
     *
     * WHY: The original getSummary() called findPendingApproval().size() which
     * loaded every pending time-entry entity into the JVM just to count them.
     * This query returns a single long without materialising any entity.
     */
    @Query("""
            SELECT COUNT(t) FROM TimeEntry t
            WHERE t.tenantId = :tenantId
              AND t.status   = 'SUBMITTED'
            """)
    long countPendingApproval(@Param("tenantId") UUID tenantId);

    /**
     * Sums approved/submitted hours for the WHOLE project.
     * Used for project-level reporting.
     */
    @Query("""
            SELECT COALESCE(SUM(t.hours), 0) FROM TimeEntry t
            WHERE t.projectId = :projectId
              AND t.status   <> 'REJECTED'
            """)
    BigDecimal sumHoursByProject(@Param("projectId") UUID projectId);

    /**
     * Sums approved/submitted hours for a SPECIFIC TASK.
     *
     * WHY THIS WAS MISSING:
     * The original logTime() called sumHoursByProject(projectId) — wrong scope —
     * to update a task's actual_hours.  This method fixes that.  A task's
     * actual_hours should only reflect time logged against THAT task, not the
     * whole project.
     */
    @Query("""
            SELECT COALESCE(SUM(t.hours), 0) FROM TimeEntry t
            WHERE t.taskId = :taskId
              AND t.status <> 'REJECTED'
            """)
    BigDecimal sumHoursByTask(@Param("taskId") UUID taskId);

    @Query("SELECT t FROM TimeEntry t WHERE t.tenantId = :tenantId AND t.id = :id")
    Optional<TimeEntry> findByTenantAndId(@Param("tenantId") UUID tenantId,
                                          @Param("id")       UUID id);
}
