package za.co.handyflow.platform.internalaudit.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.co.handyflow.platform.internalaudit.domain.model.AuditFollowUpLink;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AuditFollowUpLinkRepository extends JpaRepository<AuditFollowUpLink, UUID> {
    Optional<AuditFollowUpLink> findByTenantIdAndEngagementId(UUID tenantId, UUID engagementId);
    List<AuditFollowUpLink> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
