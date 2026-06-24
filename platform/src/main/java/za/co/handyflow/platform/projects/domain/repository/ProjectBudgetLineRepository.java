package za.co.handyflow.platform.projects.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.projects.domain.model.ProjectBudgetLine;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectBudgetLineRepository extends JpaRepository<ProjectBudgetLine, UUID> {

    @Query("SELECT b FROM ProjectBudgetLine b WHERE b.projectId = :projectId ORDER BY b.sortOrder, b.category")
    List<ProjectBudgetLine> findByProject(UUID projectId);

    @Query("SELECT b FROM ProjectBudgetLine b WHERE b.phaseId = :phaseId ORDER BY b.sortOrder")
    List<ProjectBudgetLine> findByPhase(UUID phaseId);

    @Query("SELECT b FROM ProjectBudgetLine b WHERE b.tenantId = :tenantId AND b.id = :id")
    Optional<ProjectBudgetLine> findByTenantAndId(UUID tenantId, UUID id);

    @Query("SELECT COALESCE(SUM(b.budgetedAmount), 0) FROM ProjectBudgetLine b WHERE b.projectId = :projectId")
    BigDecimal sumBudgetedByProject(UUID projectId);

    @Query("SELECT COALESCE(SUM(b.actualAmount), 0) FROM ProjectBudgetLine b WHERE b.projectId = :projectId")
    BigDecimal sumActualByProject(UUID projectId);

    @Query("SELECT COALESCE(SUM(b.committedAmount), 0) FROM ProjectBudgetLine b WHERE b.projectId = :projectId")
    BigDecimal sumCommittedByProject(UUID projectId);

    @Query("SELECT COALESCE(MAX(b.sortOrder), 0) FROM ProjectBudgetLine b WHERE b.projectId = :projectId")
    int findMaxSortOrder(UUID projectId);
}
