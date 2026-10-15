package za.co.handyflow.platform.internalaudit.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.co.handyflow.platform.internalaudit.domain.model.AuditReportPerson;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AuditReportPersonRepository extends JpaRepository<AuditReportPerson, UUID> {
    List<AuditReportPerson> findByTenantIdAndReportIdOrderByAddedAt(UUID tenantId, UUID reportId);
    List<AuditReportPerson> findByTenantIdAndUserId(UUID tenantId, UUID userId);
    Optional<AuditReportPerson> findByTenantIdAndReportIdAndKindAndUserId(UUID tenantId, UUID reportId, String kind, UUID userId);
    Optional<AuditReportPerson> findByTenantIdAndId(UUID tenantId, UUID id);
}
