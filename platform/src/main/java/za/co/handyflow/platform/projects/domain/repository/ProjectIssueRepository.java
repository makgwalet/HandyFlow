package za.co.handyflow.platform.projects.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.projects.domain.model.ProjectIssue;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectIssueRepository extends JpaRepository<ProjectIssue, UUID> {

    @Query("SELECT i FROM ProjectIssue i WHERE i.tenantId = :tenantId AND i.projectId = :projectId ORDER BY i.createdAt DESC")
    List<ProjectIssue> findByProject(@Param("tenantId") UUID tenantId, @Param("projectId") UUID projectId);

    @Query("SELECT i FROM ProjectIssue i WHERE i.tenantId = :tenantId AND i.id = :id")
    Optional<ProjectIssue> findByTenantAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);
}
