package za.co.handyflow.platform.projects.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.projects.domain.model.ProjectResource;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectResourceRepository extends JpaRepository<ProjectResource, UUID> {

    @Query("""
            SELECT r FROM ProjectResource r
            WHERE r.projectId = :projectId
            ORDER BY r.resourceType, r.createdAt
            """)
    List<ProjectResource> findByProject(@Param("projectId") UUID projectId);

    @Query("SELECT r FROM ProjectResource r WHERE r.tenantId = :tenantId AND r.id = :id")
    Optional<ProjectResource> findByTenantAndId(@Param("tenantId") UUID tenantId,
                                                @Param("id")       UUID id);

    /**
     * Finds overlapping HUMAN resource assignments for double-booking detection.
     *
     * WHY THIS EXISTS:
     * ResourceService previously ran conflict detection code that called this type
     * of query but then discarded the results silently with a TODO comment.
     * Now ResourceService.assignResource() returns the conflicts as warnings in
     * the API response body (see AssignResourceResult / ResourceAssignmentResponse).
     *
     * DATE OVERLAP LOGIC:
     * Two date ranges [A, B] and [C, D] overlap when: A <= D AND B >= C.
     * NULL end date means "open-ended" — modelled as a very far future date.
     * We use COALESCE(r.endDate, '9999-12-31') to handle this cleanly.
     *
     * Excludes the current project so resource re-use within the same project
     * does not trigger a false conflict.
     */
    @Query("""
            SELECT r FROM ProjectResource r
            WHERE r.tenantId    = :tenantId
              AND r.resourceId  = :resourceId
              AND r.projectId  <> :excludeProjectId
              AND r.startDate  <= COALESCE(:to,   :from)
              AND COALESCE(r.endDate, '9999-12-31') >= :from
            ORDER BY r.startDate
            """)
    List<ProjectResource> findConflicts(@Param("tenantId")          UUID tenantId,
                                        @Param("resourceId")         UUID resourceId,
                                        @Param("from")               LocalDate from,
                                        @Param("to")                 LocalDate to,
                                        @Param("excludeProjectId")   UUID excludeProjectId);

    /**
     * Cross-project version — used when we don't have the current project context
     * (e.g. proactive scheduling advisor).
     */
    @Query("""
            SELECT r FROM ProjectResource r
            WHERE r.tenantId   = :tenantId
              AND r.resourceId = :resourceId
              AND r.startDate  <= COALESCE(:to, :from)
              AND COALESCE(r.endDate, '9999-12-31') >= :from
            ORDER BY r.startDate
            """)
    List<ProjectResource> findByResourceAndDateRange(@Param("tenantId")   UUID tenantId,
                                                     @Param("resourceId") UUID resourceId,
                                                     @Param("from")       LocalDate from,
                                                     @Param("to")         LocalDate to);
}
