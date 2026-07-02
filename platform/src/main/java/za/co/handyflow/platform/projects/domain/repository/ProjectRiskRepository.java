package za.co.handyflow.platform.projects.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.projects.domain.model.ProjectRisk;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectRiskRepository extends JpaRepository<ProjectRisk, UUID> {

    /**
     * Ordered by risk score descending (highest risk first) — DB-side sort.
     * risk_score is a GENERATED ALWAYS AS (probability * impact) STORED column
     * in PostgreSQL, so we can ORDER BY it directly.
     */
    @Query(nativeQuery = true, value = """
            SELECT * FROM project_risks
            WHERE project_id = :projectId
            ORDER BY risk_score DESC, updated_at DESC
            """)
    List<ProjectRisk> findByProject(@Param("projectId") UUID projectId);

    @Query("""
            SELECT r FROM ProjectRisk r
            WHERE r.tenantId = :tenantId
              AND r.rating   = 'RED'
              AND r.status   = 'OPEN'
            ORDER BY r.updatedAt DESC
            """)
    List<ProjectRisk> findOpenRedRisks(@Param("tenantId") UUID tenantId);

    /**
     * COUNT variant for the dashboard KPI — avoids loading full entities.
     *
     * WHY: getSummary() previously called findOpenRedRisks().size() which
     * hydrated every ProjectRisk entity to count them.
     */
    @Query("""
            SELECT COUNT(r) FROM ProjectRisk r
            WHERE r.tenantId = :tenantId
              AND r.rating   = 'RED'
              AND r.status   = 'OPEN'
            """)
    long countOpenRedRisks(@Param("tenantId") UUID tenantId);

    @Query("SELECT r FROM ProjectRisk r WHERE r.tenantId = :tenantId AND r.id = :id")
    Optional<ProjectRisk> findByTenantAndId(@Param("tenantId") UUID tenantId,
                                            @Param("id")       UUID id);

    /**
     * Returns risks escalated to RED or AMBER in the last 24 hours.
     * Result columns: [tenantId, projectName, riskTitle, rating]
     */
    @Query(value = """
            SELECT r.tenant_id, p.name, r.title, r.rating
            FROM project_risks r
            JOIN projects p ON p.id = r.project_id
            WHERE r.rating     IN ('RED', 'AMBER')
              AND r.status      = 'OPEN'
              AND r.updated_at >= :since
              AND p.status     IN ('ACTIVE', 'ON_HOLD')
            ORDER BY r.rating, r.risk_score DESC
            """, nativeQuery = true)
    List<Object[]> findRecentlyEscalated(@Param("since") LocalDateTime since);

}
