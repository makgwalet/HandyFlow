package za.co.handyflow.platform.internalaudit.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.co.handyflow.platform.internalaudit.domain.model.AuditInvestigationLogEntry;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AuditInvestigationLogRepository extends JpaRepository<AuditInvestigationLogEntry, UUID> {
    List<AuditInvestigationLogEntry> findByTenantIdAndInvestigationIdOrderBySeqAsc(UUID tenantId, UUID investigationId);
    Optional<AuditInvestigationLogEntry> findFirstByTenantIdAndInvestigationIdOrderBySeqDesc(UUID tenantId, UUID investigationId);
    Optional<AuditInvestigationLogEntry> findFirstByTenantIdAndInvestigationIdAndActorIdAndActionOrderBySeqDesc(UUID tenantId, UUID investigationId, UUID actorId, String action);
}
