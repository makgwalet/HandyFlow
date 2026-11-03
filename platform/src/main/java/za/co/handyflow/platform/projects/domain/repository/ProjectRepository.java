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

/**
 * CHUNK 2b: added the *ForMember variants. They return only projects the given
 * user is a member of, and are used when app.projects.restrict-to-members is on
 * and the caller isn't a PM_ADMIN. The tenant-wide originals are unchanged.
 */
public interface ProjectRepository extends JpaRepository<Project, UUID> {

    // ── Paginated queries (tenant-wide) ──────────────────────────────────────

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

    // ── Paginated queries (member-scoped) ────────────────────────────────────

    @Query("""
            SELECT p FROM Project p
            WHERE p.tenantId = :tenantId
              AND p.status <> 'CANCELLED'
              AND EXISTS (SELECT 1 FROM ProjectMember m WHERE m.projectId = p.id AND m.userId = :userId)
            ORDER BY p.createdAt DESC
            """)
    Page<Project> findActiveForMember(@Param("tenantId") UUID tenantId,
                                      @Param("userId")   UUID userId,
                                      Pageable pageable);

    @Query("""
            SELECT p FROM Project p
            WHERE p.tenantId = :tenantId
              AND p.status = :status
              AND EXISTS (SELECT 1 FROM ProjectMember m WHERE m.projectId = p.id AND m.userId = :userId)
            ORDER BY p.createdAt DESC
            """)
    Page<Project> findByStatusForMember(@Param("tenantId") UUID tenantId,
                                        @Param("userId")   UUID userId,
                                        @Param("status")   ProjectStatus status,
                                        Pageable pageable);

    // ── Single-entity lookups ────────────────────────────────────────────────

    @Query("SELECT p FROM Project p WHERE p.tenantId = :tenantId AND p.id = :id")
    Optional<Project> findByTenantAndId(@Param("tenantId") UUID tenantId,
                                        @Param("id")       UUID id);

    /**
     * Not-cancelled projects whose name equals the given one, ignoring case and surrounding spaces.
     * Used by ProjectsFacade (Earthmoving site reconciliation). More than one row means ambiguous.
     */
    @Query("""
            SELECT p FROM Project p
            WHERE p.tenantId = :tenantId
              AND p.status <> 'CANCELLED'
              AND LOWER(TRIM(p.name)) = LOWER(TRIM(:name))
            ORDER BY p.createdAt ASC
            """)
    List<Project> findActiveByExactName(@Param("tenantId") UUID tenantId, @Param("name") String name);

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

    // ── Dashboard KPI counts ─────────────────────────────────────────────────

    @Query("SELECT COUNT(p) FROM Project p WHERE p.tenantId = :tenantId AND p.status = 'ACTIVE'")
    long countActive(@Param("tenantId") UUID tenantId);

    @Query("""
            SELECT COUNT(p) FROM Project p
            WHERE p.tenantId = :tenantId
              AND p.health  = :health
              AND p.status  = 'ACTIVE'
            """)
    long countByHealth(@Param("tenantId") UUID tenantId, @Param("health") ProjectHealth health);

    @Query("""
            SELECT COUNT(p) FROM Project p
            WHERE p.tenantId = :tenantId AND p.status = 'ACTIVE'
              AND EXISTS (SELECT 1 FROM ProjectMember m WHERE m.projectId = p.id AND m.userId = :userId)
            """)
    long countActiveForMember(@Param("tenantId") UUID tenantId, @Param("userId") UUID userId);

    @Query("""
            SELECT COUNT(p) FROM Project p
            WHERE p.tenantId = :tenantId
              AND p.health  = :health
              AND p.status  = 'ACTIVE'
              AND EXISTS (SELECT 1 FROM ProjectMember m WHERE m.projectId = p.id AND m.userId = :userId)
            """)
    long countByHealthForMember(@Param("tenantId") UUID tenantId, @Param("userId") UUID userId,
                                @Param("health") ProjectHealth health);

    // ── Batch stats query — fixes the N+1 problem ────────────────────────────

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