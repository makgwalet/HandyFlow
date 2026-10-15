package za.co.handyflow.platform.auditor.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.co.handyflow.platform.auditor.domain.model.ExternalAuditEngagement;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExternalAuditEngagementRepository extends JpaRepository<ExternalAuditEngagement, UUID> {
    List<ExternalAuditEngagement> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
    Optional<ExternalAuditEngagement> findByTenantIdAndId(UUID tenantId, UUID id);
}
