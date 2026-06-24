package za.co.handyflow.platform.projects.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.projects.domain.model.ProjectResource;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectResourceRepository extends JpaRepository<ProjectResource, UUID> {

    @Query("SELECT r FROM ProjectResource r WHERE r.projectId = :projectId ORDER BY r.resourceType, r.resourceName")
    List<ProjectResource> findByProject(UUID projectId);

    @Query("SELECT r FROM ProjectResource r WHERE r.taskId = :taskId ORDER BY r.resourceName")
    List<ProjectResource> findByTask(UUID taskId);

    /** Find all assignments for a resource — used to detect conflicts */
    @Query("SELECT r FROM ProjectResource r WHERE r.tenantId = :tenantId AND r.resourceId = :resourceId AND r.endDate >= :from AND (r.startDate IS NULL OR r.startDate <= :to)")
    List<ProjectResource> findByResourceAndDateRange(UUID tenantId, UUID resourceId, LocalDate from, LocalDate to);

    @Query("SELECT r FROM ProjectResource r WHERE r.tenantId = :tenantId AND r.id = :id")
    Optional<ProjectResource> findByTenantAndId(UUID tenantId, UUID id);
}
