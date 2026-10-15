package za.co.handyflow.platform.internalaudit.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.internalaudit.domain.model.AuditWorkpaperLink;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AuditWorkpaperLinkRepository extends JpaRepository<AuditWorkpaperLink, UUID> {

    @Query("SELECT l FROM AuditWorkpaperLink l WHERE l.tenantId = :tenantId AND l.workpaperId = :workpaperId ORDER BY l.linkedAt")
    List<AuditWorkpaperLink> findByWorkpaper(@Param("tenantId") UUID tenantId, @Param("workpaperId") UUID workpaperId);

    @Query("SELECT l FROM AuditWorkpaperLink l WHERE l.tenantId = :tenantId AND l.linkType = :type AND l.linkId = :linkId")
    List<AuditWorkpaperLink> findByTarget(@Param("tenantId") UUID tenantId, @Param("type") String type, @Param("linkId") UUID linkId);

    @Query("SELECT l FROM AuditWorkpaperLink l WHERE l.tenantId = :tenantId AND l.workpaperId = :workpaperId AND l.linkType = :type AND l.linkId = :linkId")
    Optional<AuditWorkpaperLink> find(@Param("tenantId") UUID tenantId, @Param("workpaperId") UUID workpaperId, @Param("type") String type, @Param("linkId") UUID linkId);
}
