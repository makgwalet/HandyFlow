package za.co.handyflow.platform.internalaudit.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.co.handyflow.platform.internalaudit.domain.model.AuditFollowUpItem;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AuditFollowUpItemRepository extends JpaRepository<AuditFollowUpItem, UUID> {
    List<AuditFollowUpItem> findByTenantIdAndEngagementId(UUID tenantId, UUID engagementId);
    Optional<AuditFollowUpItem> findByTenantIdAndEngagementIdAndFindingId(UUID tenantId, UUID engagementId, UUID findingId);
    List<AuditFollowUpItem> findByTenantId(UUID tenantId);
}
