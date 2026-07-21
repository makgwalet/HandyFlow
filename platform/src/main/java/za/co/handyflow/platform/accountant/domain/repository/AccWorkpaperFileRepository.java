package za.co.handyflow.platform.accountant.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import za.co.handyflow.platform.accountant.domain.model.AccWorkpaperFile;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccWorkpaperFileRepository extends JpaRepository<AccWorkpaperFile, UUID> {

    @Query("SELECT f FROM AccountantWorkpaperFile f WHERE f.tenantId = :tenantId AND f.id = :id")
    Optional<AccWorkpaperFile> findByTenantIdAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    /**
     * List view — never fetches file_content_base64, matching the same
     * projection pattern already used for AccFicaDocumentRepository.
     * Excludes soft-deleted files.
     */
    interface WorkpaperFileSummaryProjection {
        UUID getId();
        String getFileName();
        String getMimeType();
        Long getFileSizeBytes();
        String getReviewStatus();
        int getVersionNumber();
        UUID getSupersededBy();
        Instant getCreatedAt();
    }

    @Query("""
        SELECT f.id as id, f.fileName as fileName, f.mimeType as mimeType, f.fileSizeBytes as fileSizeBytes,
               f.reviewStatus as reviewStatus, f.versionNumber as versionNumber, f.supersededBy as supersededBy,
               f.createdAt as createdAt
        FROM AccountantWorkpaperFile f
        WHERE f.tenantId = :tenantId AND f.folderId = :folderId AND f.deletedAt IS NULL
        ORDER BY f.createdAt DESC
    """)
    List<WorkpaperFileSummaryProjection> findSummariesByFolder(@Param("tenantId") UUID tenantId,
                                                               @Param("folderId") UUID folderId);

    /**
     * The current (non-superseded, non-deleted) version of a file by
     * name within a folder — backs versioning on re-upload. If found,
     * a new upload becomes the next version and supersedes this one
     * rather than creating an unrelated duplicate.
     */
    @Query("""
        SELECT f FROM AccountantWorkpaperFile f
        WHERE f.tenantId = :tenantId AND f.folderId = :folderId AND f.fileName = :fileName
          AND f.supersededBy IS NULL AND f.deletedAt IS NULL
    """)
    Optional<AccWorkpaperFile> findCurrentVersionByName(@Param("tenantId") UUID tenantId,
                                                        @Param("folderId") UUID folderId,
                                                        @Param("fileName") String fileName);
}