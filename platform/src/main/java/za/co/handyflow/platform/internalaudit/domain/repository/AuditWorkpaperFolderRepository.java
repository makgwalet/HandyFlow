package za.co.handyflow.platform.internalaudit.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.internalaudit.domain.model.AuditWorkpaperFolder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AuditWorkpaperFolderRepository extends JpaRepository<AuditWorkpaperFolder, UUID> {

    @Query("SELECT f FROM AuditWorkpaperFolder f WHERE f.tenantId = :tenantId AND f.id = :id")
    Optional<AuditWorkpaperFolder> findByTenantAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    @Query("SELECT f FROM AuditWorkpaperFolder f WHERE f.tenantId = :tenantId AND f.engagementId = :engagementId ORDER BY f.sortOrder, f.name")
    List<AuditWorkpaperFolder> findByEngagement(@Param("tenantId") UUID tenantId, @Param("engagementId") UUID engagementId);
}
