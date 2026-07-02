package za.co.handyflow.platform.projects.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.projects.domain.model.ProjectRfi;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectRfiRepository extends JpaRepository<ProjectRfi, UUID> {

    @Query("SELECT r FROM ProjectRfi r WHERE r.projectId = :projectId AND r.tenantId = :tenantId ORDER BY r.requestedDate DESC, r.createdAt DESC")
    List<ProjectRfi> findByProject(@Param("projectId") UUID projectId, @Param("tenantId") UUID tenantId);

    @Query("SELECT r FROM ProjectRfi r WHERE r.projectId = :projectId AND r.tenantId = :tenantId AND r.status = :status ORDER BY r.requestedDate DESC")
    List<ProjectRfi> findByProjectAndStatus(@Param("projectId") UUID projectId, @Param("tenantId") UUID tenantId, @Param("status") String status);

    Optional<ProjectRfi> findByIdAndTenantId(UUID id, UUID tenantId);

    @Query("SELECT COUNT(r) FROM ProjectRfi r WHERE r.projectId = :projectId AND r.tenantId = :tenantId AND r.status IN ('SUBMITTED','RESPONDED')")
    long countOpenByProject(@Param("projectId") UUID projectId, @Param("tenantId") UUID tenantId);
}
