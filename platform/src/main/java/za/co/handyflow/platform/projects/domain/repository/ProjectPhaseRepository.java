package za.co.handyflow.platform.projects.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.projects.domain.model.ProjectPhase;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectPhaseRepository extends JpaRepository<ProjectPhase, UUID> {

    @Query("""
            SELECT p FROM ProjectPhase p
            WHERE p.projectId = :projectId
            ORDER BY p.sortOrder, p.createdAt
            """)
    List<ProjectPhase> findByProject(@Param("projectId") UUID projectId);

    @Query("SELECT p FROM ProjectPhase p WHERE p.tenantId = :tenantId AND p.id = :id")
    Optional<ProjectPhase> findByTenantAndId(@Param("tenantId") UUID tenantId,
                                             @Param("id")       UUID id);

    /**
     * Used only when SequenceService is not available for sort_order.
     * Prefer SequenceService.nextSortOrder() for new phases to avoid the
     * MAX + 1 race condition.  Sort order gaps are acceptable; duplicates are not.
     */
    @Query("""
            SELECT COALESCE(MAX(p.sortOrder), 0) FROM ProjectPhase p
            WHERE p.projectId = :projectId
            """)
    int findMaxSortOrder(@Param("projectId") UUID projectId);
}
