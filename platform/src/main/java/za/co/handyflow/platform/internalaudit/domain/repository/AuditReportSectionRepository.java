package za.co.handyflow.platform.internalaudit.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.co.handyflow.platform.internalaudit.domain.model.AuditReportSection;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AuditReportSectionRepository extends JpaRepository<AuditReportSection, UUID> {
    List<AuditReportSection> findByTenantIdAndReportIdOrderByPosition(UUID tenantId, UUID reportId);
    Optional<AuditReportSection> findByTenantIdAndReportIdAndPosition(UUID tenantId, UUID reportId, int position);
}
