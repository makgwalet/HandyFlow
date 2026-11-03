package za.co.handyflow.platform.projects.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.projects.domain.model.ProjectDocument;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * CHUNK 6: the old findCurrentByProjectAndType() is REMOVED — it selected every CURRENT/APPROVED
 * document of a TYPE, which is why one new revision used to supersede unrelated documents.
 * Revisions are now found by document number (findSeries).
 */
public interface ProjectDocumentRepository extends JpaRepository<ProjectDocument, UUID> {

    /** The working register: everything except voided documents. */
    @Query("""
            SELECT d FROM ProjectDocument d
            WHERE d.projectId = :projectId AND d.status <> 'VOIDED'
            ORDER BY d.documentType, d.documentNumber, d.createdAt DESC
            """)
    List<ProjectDocument> findRegister(@Param("projectId") UUID projectId);

    @Query("""
            SELECT d FROM ProjectDocument d
            WHERE d.projectId = :projectId AND d.documentType = :type AND d.status <> 'VOIDED'
            ORDER BY d.documentNumber, d.createdAt DESC
            """)
    List<ProjectDocument> findRegisterByType(@Param("projectId") UUID projectId, @Param("type") String type);

    /** Only documents in force or in progress (CURRENT, FOR_REVIEW, APPROVED). */
    @Query("""
            SELECT d FROM ProjectDocument d
            WHERE d.projectId = :projectId AND d.status IN ('CURRENT','FOR_REVIEW','APPROVED')
            ORDER BY d.documentType, d.documentNumber, d.createdAt DESC
            """)
    List<ProjectDocument> findLive(@Param("projectId") UUID projectId);

    @Query("""
            SELECT d FROM ProjectDocument d
            WHERE d.projectId = :projectId AND d.documentType = :type AND d.status IN ('CURRENT','FOR_REVIEW','APPROVED')
            ORDER BY d.documentNumber, d.createdAt DESC
            """)
    List<ProjectDocument> findLiveByType(@Param("projectId") UUID projectId, @Param("type") String type);

    /** Every revision of one document, newest first, INCLUDING superseded and voided ones. */
    @Query("""
            SELECT d FROM ProjectDocument d
            WHERE d.projectId = :projectId AND d.documentNumber = :number
            ORDER BY d.createdAt DESC
            """)
    List<ProjectDocument> findSeries(@Param("projectId") UUID projectId, @Param("number") String number);

    /** What the client portal may list: marked CLIENT and APPROVED. */
    @Query("""
            SELECT d FROM ProjectDocument d
            WHERE d.projectId = :projectId AND d.visibility = 'CLIENT' AND d.status = 'APPROVED'
            ORDER BY d.documentType, d.documentNumber
            """)
    List<ProjectDocument> findClientVisible(@Param("projectId") UUID projectId);

    @Query("SELECT d FROM ProjectDocument d WHERE d.tenantId = :tenantId AND d.id = :id")
    Optional<ProjectDocument> findByTenantAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);
}