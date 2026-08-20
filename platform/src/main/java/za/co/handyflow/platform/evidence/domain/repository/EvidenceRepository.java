package za.co.handyflow.platform.evidence.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.evidence.domain.model.Evidence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EvidenceRepository extends JpaRepository<Evidence, UUID> {

    @Query("""
        SELECT e FROM Evidence e
        WHERE e.tenantId = :tenantId AND e.id = :id
    """)
    Optional<Evidence> findByTenantAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    /**
     * Metadata-only projection for list views — never fetches anything
     * requiring a retrieve() call to FileStorageService. Same reasoning
     * as AccFicaDocumentRepository.FicaDocSummaryProjection: a list of
     * ten attachments shouldn't mean ten storage reads just to render
     * file names and sizes.
     */
    interface EvidenceSummaryProjection {
        UUID getId();
        String getFileName();
        String getContentType();
        Long getFileSizeBytes();
        String getEvidenceType();
        String getStatus();
        String getUploadedByName();
        Instant getCreatedAt();
    }

    @Query("""
        SELECT e.id as id, e.fileName as fileName, e.contentType as contentType,
               e.fileSizeBytes as fileSizeBytes, e.evidenceType as evidenceType,
               e.status as status, e.uploadedByName as uploadedByName, e.createdAt as createdAt
        FROM Evidence e
        WHERE e.tenantId = :tenantId
          AND e.sourceModule = :sourceModule
          AND e.relatedEntityType = :relatedEntityType
          AND e.relatedEntityId = :relatedEntityId
          AND e.status = 'ACTIVE'
        ORDER BY e.createdAt DESC
    """)
    List<EvidenceSummaryProjection> findActiveForEntity(
            @Param("tenantId") UUID tenantId,
            @Param("sourceModule") String sourceModule,
            @Param("relatedEntityType") String relatedEntityType,
            @Param("relatedEntityId") UUID relatedEntityId);

    // NEW: Stage 3 — everything for a tenant, across every module/entity,
    // not scoped to one specific record. An auditor browsing evidence
    // doesn't already know which specific record they want to look at.
    @Query("""
        SELECT e.id as id, e.fileName as fileName, e.contentType as contentType,
               e.fileSizeBytes as fileSizeBytes, e.evidenceType as evidenceType,
               e.status as status, e.uploadedByName as uploadedByName, e.createdAt as createdAt
        FROM Evidence e
        WHERE e.tenantId = :tenantId AND e.status = 'ACTIVE'
        ORDER BY e.createdAt DESC
    """)
    List<EvidenceSummaryProjection> findAllActiveForTenant(@Param("tenantId") UUID tenantId);
}