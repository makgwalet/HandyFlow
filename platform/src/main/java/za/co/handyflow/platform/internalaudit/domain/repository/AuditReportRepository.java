package za.co.handyflow.platform.internalaudit.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.co.handyflow.platform.internalaudit.domain.model.AuditReport;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AuditReportRepository extends JpaRepository<AuditReport, UUID> {
    Optional<AuditReport> findByTenantIdAndId(UUID tenantId, UUID id);
    List<AuditReport> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
    List<AuditReport> findByTenantIdAndEngagementIdOrderByVersionDesc(UUID tenantId, UUID engagementId);
    List<AuditReport> findByTenantIdAndIdIn(UUID tenantId, java.util.Collection<UUID> ids);
}
