package za.co.handyflow.platform.projects.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.projects.domain.model.Project;
import za.co.handyflow.platform.projects.domain.enums.ProjectHealth;
import za.co.handyflow.platform.projects.domain.enums.ProjectStatus;
import za.co.handyflow.platform.projects.domain.repository.projections.ProjectStatsSummary;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectRepository extends JpaRepository<Project, UUID> {

    // ── Paginated queries ────────────────────────────────────────────────────

    @Query("""
            SELECT p FROM Project p
            WHERE p.tenantId = :tenantId
              AND p.status <> 'CANCELLED'
            ORDER BY p.createdAt DESC
            """)
    Page<Project> findActive(@Param("tenantId") UUID tenantId, Pageable pageable);

    @Query("""
            SELECT p FROM Project p
            WHERE p.tenantId = :tenantId
              AND p.status = :status
            ORDER BY p.createdAt DESC
            """)
    Page<Project> findByStatus(@Param("tenantId") UUID tenantId,
                               @Param("status")   ProjectStatus status,
                               Pageable pageable);

    // ── Single-entity lookups ────────────────────────────────────────────────

    @Query("SELECT p FROM Project p WHERE p.tenantId = :tenantId AND p.id = :id")
    Optional<Project> findByTenantAndId(@Param("tenantId") UUID tenantId,
                                        @Param("id")       UUID id);

    @Query("SELECT p FROM Project p WHERE p.clientPortalToken = :token")
    Optional<Project> findByPortalToken(@Param("token") String token);

    @Query("""
            SELECT p FROM Project p
            WHERE p.tenantId = :tenantId
              AND p.clientId = :clientId
              AND p.status <> 'CANCELLED'
            ORDER BY p.createdAt DESC
            """)
    List<Project> findByClient(@Param("tenantId") UUID tenantId,
                               @Param("clientId") UUID clientId);

    @Query("""
            SELECT p FROM Project p
            WHERE p.tenantId = :tenantId
              AND p.projectManagerId = :managerId
              AND p.status = 'ACTIVE'
            ORDER BY p.createdAt DESC
            """)
    List<Project> findActiveByManager(@Param("tenantId")  UUID tenantId,
                                      @Param("managerId") UUID managerId);

    // ── Dashboard KPI counts (use COUNT — never load entities just to call .size()) ──

    @Query("SELECT COUNT(p) FROM Project p WHERE p.tenantId = :tenantId AND p.status = 'ACTIVE'")
    long countActive(@Param("tenantId") UUID tenantId);

    @Query("""
            SELECT COUNT(p) FROM Project p
            WHERE p.tenantId = :tenantId
              AND p.health  = :health
              AND p.status  = 'ACTIVE'
            """)
    long countByHealth(@Param("tenantId") UUID tenantId, @Param("health") ProjectHealth health);

    // ── Batch stats query — fixes the N+1 problem ────────────────────────────
    /**
     * Returns task and risk counts for a LIST of project IDs in a single round-trip.
     *
     * WHY THIS EXISTS:
     * The original ProjectController.getProjects() called getTasks() + getRisks()
     * inside a page.map() loop.  With page size = 20 that was 41 DB round-trips.
     * This one native query replaces those 40 extra calls.
     *
     * HOW IT WORKS:
     * LEFT JOIN so projects with zero tasks / risks still appear in results.
     * FILTER (WHERE ...) is PostgreSQL-specific aggregate filtering — cleaner than
     * a nested CASE WHEN inside SUM.
     * p.id = ANY(:projectIds) is the PostgreSQL idiom for WHERE id IN (:list)
     * and avoids the "parameter limit" issues that IN (...) can hit on large arrays.
     */
    @Query(nativeQuery = true, value = """
            SELECT
                p.id::text                                                          AS projectId,
                COUNT(DISTINCT t.id)                                                AS taskCount,
                COUNT(DISTINCT t.id) FILTER (WHERE t.status = 'COMPLETED')         AS completedTaskCount,
                COUNT(DISTINCT r.id) FILTER (WHERE r.status = 'OPEN')              AS openRiskCount
            FROM projects p
            LEFT JOIN project_tasks t ON t.project_id = p.id
            LEFT JOIN project_risks r ON r.project_id = p.id
            WHERE p.id = ANY(:projectIds)
            GROUP BY p.id
            """)
    List<ProjectStatsSummary> findProjectStats(@Param("projectIds") UUID[] projectIds);
}