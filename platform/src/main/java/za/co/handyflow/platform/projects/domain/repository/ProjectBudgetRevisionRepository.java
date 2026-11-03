package za.co.handyflow.platform.projects.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.projects.domain.model.ProjectBudgetRevision;

import java.util.List;
import java.util.UUID;

public interface ProjectBudgetRevisionRepository extends JpaRepository<ProjectBudgetRevision, UUID> {

    @Query("""
            SELECT r FROM ProjectBudgetRevision r
            WHERE r.tenantId = :tenantId AND r.projectId = :projectId
            ORDER BY r.revisedAt DESC
            """)
    List<ProjectBudgetRevision> findByProject(@Param("tenantId") UUID tenantId,
                                              @Param("projectId") UUID projectId);
}