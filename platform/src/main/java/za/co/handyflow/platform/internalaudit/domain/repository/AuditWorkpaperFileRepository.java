package za.co.handyflow.platform.internalaudit.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.internalaudit.domain.model.AuditWorkpaperFile;
import za.co.handyflow.platform.internalaudit.dto.WorkpaperFileResponse;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AuditWorkpaperFileRepository extends JpaRepository<AuditWorkpaperFile, UUID> {

    @Query("SELECT f FROM AuditWorkpaperFile f WHERE f.tenantId = :tenantId AND f.id = :id")
    Optional<AuditWorkpaperFile> findByTenantAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    /**
     * The current version of every workpaper in an engagement, in one query and without the file contents (which are stored in the row and
     * can be megabytes each). Use this for lists and counts; load the entity only when the file itself is needed.
     */
    @Query("SELECT new za.co.handyflow.platform.internalaudit.dto.WorkpaperFileResponse(f.id, f.folderId, f.fileName, f.mimeType, f.fileSizeBytes, f.reviewStatus, "
            + "f.preparedBy, f.preparedAt, f.reviewedBy, f.reviewedAt, f.signedOffBy, f.signedOffAt, f.versionNumber, f.supersededBy, f.createdAt) "
            + "FROM AuditWorkpaperFile f WHERE f.tenantId = :tenantId AND f.engagementId = :engagementId AND f.deletedAt IS NULL AND f.supersededBy IS NULL ORDER BY f.updatedAt DESC")
    List<WorkpaperFileResponse> currentForEngagement(@Param("tenantId") UUID tenantId, @Param("engagementId") UUID engagementId);

    @Query("SELECT f FROM AuditWorkpaperFile f WHERE f.tenantId = :tenantId AND f.folderId = :folderId AND f.deletedAt IS NULL ORDER BY f.fileName, f.versionNumber DESC")
    List<AuditWorkpaperFile> findActiveByFolder(@Param("tenantId") UUID tenantId, @Param("folderId") UUID folderId);

    @Query("SELECT f FROM AuditWorkpaperFile f WHERE f.tenantId = :tenantId AND f.folderId = :folderId AND f.deletedAt IS NOT NULL ORDER BY f.updatedAt DESC")
    List<AuditWorkpaperFile> findDeletedByFolder(@Param("tenantId") UUID tenantId, @Param("folderId") UUID folderId);

    // Backs versioning-on-reupload — finds the current (non-superseded,
    // active) file with this exact name in this folder, if one exists.
    @Query("SELECT f FROM AuditWorkpaperFile f WHERE f.tenantId = :tenantId AND f.folderId = :folderId AND f.fileName = :fileName AND f.deletedAt IS NULL AND f.supersededBy IS NULL ORDER BY f.versionNumber DESC")
    List<AuditWorkpaperFile> findCurrentVersionCandidates(@Param("tenantId") UUID tenantId, @Param("folderId") UUID folderId, @Param("fileName") String fileName);

    default Optional<AuditWorkpaperFile> findCurrentVersionByName(UUID tenantId, UUID folderId, String fileName) {
        List<AuditWorkpaperFile> results = findCurrentVersionCandidates(tenantId, folderId, fileName);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    /** As {@link #findActiveByFolder} but without the file contents, for lists. */
    @Query("SELECT new za.co.handyflow.platform.internalaudit.dto.WorkpaperFileResponse(f.id, f.folderId, f.fileName, f.mimeType, f.fileSizeBytes, f.reviewStatus, "
            + "f.preparedBy, f.preparedAt, f.reviewedBy, f.reviewedAt, f.signedOffBy, f.signedOffAt, f.versionNumber, f.supersededBy, f.createdAt) "
            + "FROM AuditWorkpaperFile f WHERE f.tenantId = :tenantId AND f.folderId = :folderId AND f.deletedAt IS NULL ORDER BY f.fileName, f.versionNumber DESC")
    List<WorkpaperFileResponse> listActiveByFolder(@Param("tenantId") UUID tenantId, @Param("folderId") UUID folderId);

    /** As {@link #findDeletedByFolder} but without the file contents, for lists. */
    @Query("SELECT new za.co.handyflow.platform.internalaudit.dto.WorkpaperFileResponse(f.id, f.folderId, f.fileName, f.mimeType, f.fileSizeBytes, f.reviewStatus, "
            + "f.preparedBy, f.preparedAt, f.reviewedBy, f.reviewedAt, f.signedOffBy, f.signedOffAt, f.versionNumber, f.supersededBy, f.createdAt) "
            + "FROM AuditWorkpaperFile f WHERE f.tenantId = :tenantId AND f.folderId = :folderId AND f.deletedAt IS NOT NULL ORDER BY f.updatedAt DESC")
    List<WorkpaperFileResponse> listDeletedByFolder(@Param("tenantId") UUID tenantId, @Param("folderId") UUID folderId);
}
