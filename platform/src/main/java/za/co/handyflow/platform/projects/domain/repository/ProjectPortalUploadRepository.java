package za.co.handyflow.platform.projects.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.projects.domain.model.ProjectPortalUpload;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectPortalUploadRepository extends JpaRepository<ProjectPortalUpload, UUID> {

    @Query("SELECT u FROM ProjectPortalUpload u WHERE u.tenantId = :tenantId AND u.projectId = :projectId ORDER BY u.createdAt DESC")
    List<ProjectPortalUpload> findByProject(@Param("tenantId") UUID tenantId, @Param("projectId") UUID projectId);

    @Query("SELECT u FROM ProjectPortalUpload u WHERE u.grantId IN :grantIds AND u.projectId = :projectId ORDER BY u.createdAt DESC")
    List<ProjectPortalUpload> findByGrantsAndProject(@Param("grantIds") Collection<UUID> grantIds, @Param("projectId") UUID projectId);

    @Query("SELECT u FROM ProjectPortalUpload u WHERE u.tenantId = :tenantId AND u.id = :id")
    Optional<ProjectPortalUpload> findByTenantAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);
}
