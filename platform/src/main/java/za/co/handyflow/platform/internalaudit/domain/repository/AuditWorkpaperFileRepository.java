package za.co.handyflow.platform.internalaudit.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.internalaudit.domain.model.AuditWorkpaperFile;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AuditWorkpaperFileRepository extends JpaRepository<AuditWorkpaperFile, UUID> {

    @Query("SELECT f FROM AuditWorkpaperFile f WHERE f.tenantId = :tenantId AND f.id = :id")
    Optional<AuditWorkpaperFile> findByTenantAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

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
}
