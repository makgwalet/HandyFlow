package za.co.handyflow.platform.internalaudit.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.co.handyflow.platform.internalaudit.domain.model.AuditMonitorAlert;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AuditMonitorAlertRepository extends JpaRepository<AuditMonitorAlert, UUID> {
    Optional<AuditMonitorAlert> findByTenantIdAndId(UUID tenantId, UUID id);
    List<AuditMonitorAlert> findByTenantIdOrderByFirstSeenDesc(UUID tenantId);
    List<AuditMonitorAlert> findByTenantIdAndFingerprintIn(UUID tenantId, Collection<String> fingerprints);
}
