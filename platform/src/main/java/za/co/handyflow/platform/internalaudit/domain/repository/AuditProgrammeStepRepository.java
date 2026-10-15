package za.co.handyflow.platform.internalaudit.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.co.handyflow.platform.internalaudit.domain.model.AuditProgrammeStep;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AuditProgrammeStepRepository extends JpaRepository<AuditProgrammeStep, UUID> {
    List<AuditProgrammeStep> findByTenantIdAndEngagementIdOrderByControlCodeAsc(UUID tenantId, UUID engagementId);
    Optional<AuditProgrammeStep> findByTenantIdAndEngagementIdAndControlCode(UUID tenantId, UUID engagementId, String controlCode);
}
