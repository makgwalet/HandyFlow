package za.co.handyflow.platform.projects.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.projects.domain.model.ProjectDocument;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectDocumentRepository extends JpaRepository<ProjectDocument, UUID> {

    @Query("SELECT d FROM ProjectDocument d WHERE d.projectId = :projectId ORDER BY d.documentType, d.createdAt DESC")
    List<ProjectDocument> findByProject(UUID projectId);

    @Query("SELECT d FROM ProjectDocument d WHERE d.projectId = :projectId AND d.documentType = :type ORDER BY d.createdAt DESC")
    List<ProjectDocument> findByProjectAndType(UUID projectId, String type);

    @Query("SELECT d FROM ProjectDocument d WHERE d.tenantId = :tenantId AND d.id = :id")
    Optional<ProjectDocument> findByTenantAndId(UUID tenantId, UUID id);
}
