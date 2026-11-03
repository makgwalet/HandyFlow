package za.co.handyflow.platform.projects.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.projects.domain.model.ProjectDocumentLink;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectDocumentLinkRepository extends JpaRepository<ProjectDocumentLink, UUID> {

    @Query("""
            SELECT l FROM ProjectDocumentLink l
            WHERE l.tenantId = :tenantId AND l.documentId = :documentId
            ORDER BY l.linkedAt
            """)
    List<ProjectDocumentLink> findByDocument(@Param("tenantId") UUID tenantId,
                                             @Param("documentId") UUID documentId);

    /** Every link to one record — "which documents are evidence for this RFI?" */
    @Query("""
            SELECT l FROM ProjectDocumentLink l
            WHERE l.tenantId = :tenantId AND l.projectId = :projectId
              AND l.linkType = :linkType AND l.targetId = :targetId
            ORDER BY l.linkedAt
            """)
    List<ProjectDocumentLink> findByTarget(@Param("tenantId") UUID tenantId,
                                           @Param("projectId") UUID projectId,
                                           @Param("linkType") String linkType,
                                           @Param("targetId") UUID targetId);

    @Query("""
            SELECT l FROM ProjectDocumentLink l
            WHERE l.tenantId = :tenantId AND l.documentId = :documentId AND l.id = :id
            """)
    Optional<ProjectDocumentLink> findByDocumentAndId(@Param("tenantId") UUID tenantId,
                                                      @Param("documentId") UUID documentId,
                                                      @Param("id") UUID id);

    @Query("""
            SELECT COUNT(l) > 0 FROM ProjectDocumentLink l
            WHERE l.documentId = :documentId AND l.linkType = :linkType AND l.targetId = :targetId
            """)
    boolean existsLink(@Param("documentId") UUID documentId,
                       @Param("linkType") String linkType,
                       @Param("targetId") UUID targetId);
}
