package za.co.handyflow.platform.notifications.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GrcLinkRepository extends JpaRepository<GrcLink, UUID> {

    @Query("SELECT l FROM GrcLink l WHERE l.tenantId = :tenantId AND l.id = :id")
    Optional<GrcLink> findForTenant(TenantId tenantId, UUID id);

    @Query("SELECT l FROM GrcLink l WHERE l.tenantId = :tenantId AND l.linkType = :type AND l.sourceId = :sourceId AND l.targetId = :targetId")
    Optional<GrcLink> findExact(TenantId tenantId, GrcLinkType type, UUID sourceId, UUID targetId);

    @Query("SELECT l FROM GrcLink l WHERE l.tenantId = :tenantId AND l.linkType = :type AND l.sourceId = :sourceId ORDER BY l.createdAt")
    List<GrcLink> findBySource(TenantId tenantId, GrcLinkType type, UUID sourceId);

    @Query("SELECT l FROM GrcLink l WHERE l.tenantId = :tenantId AND l.linkType = :type AND l.targetId = :targetId ORDER BY l.createdAt")
    List<GrcLink> findByTarget(TenantId tenantId, GrcLinkType type, UUID targetId);

    @Query("SELECT l FROM GrcLink l WHERE l.tenantId = :tenantId AND l.linkType = :type")
    List<GrcLink> findAllOfType(TenantId tenantId, GrcLinkType type);
}
