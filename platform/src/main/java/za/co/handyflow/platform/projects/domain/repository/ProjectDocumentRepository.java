package za.co.handyflow.platform.projects.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.projects.domain.model.ProjectDocument;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectDocumentRepository extends JpaRepository<ProjectDocument, UUID> {

    @Query("""
            SELECT d FROM ProjectDocument d
            WHERE d.projectId = :projectId
            ORDER BY d.documentType, d.createdAt DESC
            """)
    List<ProjectDocument> findByProject(@Param("projectId") UUID projectId);

    @Query("""
            SELECT d FROM ProjectDocument d
            WHERE d.projectId    = :projectId
              AND d.documentType = :type
            ORDER BY d.createdAt DESC
            """)
    List<ProjectDocument> findByProjectAndType(@Param("projectId") UUID projectId,
                                               @Param("type")      String type);

    /**
     * Find documents of the same type that are currently CURRENT or APPROVED —
     * used by DocumentService to supersede the previous revision when a new
     * version is uploaded.
     */
    @Query("""
            SELECT d FROM ProjectDocument d
            WHERE d.projectId    = :projectId
              AND d.documentType = :type
              AND d.status IN ('CURRENT', 'APPROVED')
            ORDER BY d.createdAt DESC
            """)
    List<ProjectDocument> findCurrentByProjectAndType(@Param("projectId") UUID projectId,
                                                      @Param("type")      String type);

    @Query("SELECT d FROM ProjectDocument d WHERE d.tenantId = :tenantId AND d.id = :id")
    Optional<ProjectDocument> findByTenantAndId(@Param("tenantId") UUID tenantId,
                                                @Param("id")       UUID id);
}
