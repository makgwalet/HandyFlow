package za.co.handyflow.platform.projects.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.projects.domain.model.ProjectActionItem;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectActionItemRepository extends JpaRepository<ProjectActionItem, UUID> {

    @Query("SELECT a FROM ProjectActionItem a WHERE a.tenantId = :tenantId AND a.projectId = :projectId ORDER BY a.createdAt DESC")
    List<ProjectActionItem> findByProject(@Param("tenantId") UUID tenantId, @Param("projectId") UUID projectId);

    @Query("SELECT a FROM ProjectActionItem a WHERE a.tenantId = :tenantId AND a.id = :id")
    Optional<ProjectActionItem> findByTenantAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);
}
