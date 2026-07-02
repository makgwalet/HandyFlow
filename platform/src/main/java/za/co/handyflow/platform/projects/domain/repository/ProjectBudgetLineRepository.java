package za.co.handyflow.platform.projects.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.projects.domain.model.ProjectBudgetLine;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectBudgetLineRepository extends JpaRepository<ProjectBudgetLine, UUID> {

    @Query("""
            SELECT l FROM ProjectBudgetLine l
            WHERE l.projectId = :projectId
            ORDER BY l.sortOrder, l.createdAt
            """)
    List<ProjectBudgetLine> findByProject(@Param("projectId") UUID projectId);

    @Query("""
            SELECT l FROM ProjectBudgetLine l
            WHERE l.phaseId = :phaseId
            ORDER BY l.sortOrder
            """)
    List<ProjectBudgetLine> findByPhase(@Param("phaseId") UUID phaseId);

    @Query("SELECT l FROM ProjectBudgetLine l WHERE l.tenantId = :tenantId AND l.id = :id")
    Optional<ProjectBudgetLine> findByTenantAndId(@Param("tenantId") UUID tenantId,
                                                  @Param("id")       UUID id);

    // ── Aggregate queries — used to keep project.budget_total / _spent / _committed in sync ──

    /**
     * Sum of all budgeted amounts — used to refresh project.budget_total after
     * any line is created, updated, or deleted.
     *
     * WHY NOT STORE THIS IN THE PROJECT:
     * We DO store it in projects.budget_total for fast reads (dashboard KPIs,
     * list pages).  But that denormalised copy must be kept in sync.  These SUM
     * queries are the authoritative source; they are called after every mutation
     * to push the correct total back to the project row.
     */
    @Query("""
            SELECT COALESCE(SUM(l.budgetedAmount), 0) FROM ProjectBudgetLine l
            WHERE l.projectId = :projectId
            """)
    BigDecimal sumBudgetedByProject(@Param("projectId") UUID projectId);

    @Query("""
            SELECT COALESCE(SUM(l.actualAmount), 0) FROM ProjectBudgetLine l
            WHERE l.projectId = :projectId
            """)
    BigDecimal sumActualByProject(@Param("projectId") UUID projectId);

    @Query("""
            SELECT COALESCE(SUM(l.committedAmount), 0) FROM ProjectBudgetLine l
            WHERE l.projectId = :projectId
            """)
    BigDecimal sumCommittedByProject(@Param("projectId") UUID projectId);

    /**
     * Used to determine the next sort_order for a new line without loading all lines.
     * SequenceService.nextSortOrder() wraps this with the atomic counter approach,
     * but sort_order doesn't need to be globally unique — just monotonically increasing
     * within a project — so MAX + 1 is acceptable here.
     */
    @Query("""
            SELECT COALESCE(MAX(l.sortOrder), 0) FROM ProjectBudgetLine l
            WHERE l.projectId = :projectId
            """)
    int findMaxSortOrder(@Param("projectId") UUID projectId);
}
