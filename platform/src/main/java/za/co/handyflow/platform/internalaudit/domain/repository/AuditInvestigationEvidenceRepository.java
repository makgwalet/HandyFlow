package za.co.handyflow.platform.internalaudit.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.co.handyflow.platform.internalaudit.domain.model.AuditInvestigationEvidence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AuditInvestigationEvidenceRepository extends JpaRepository<AuditInvestigationEvidence, UUID> {
    List<AuditInvestigationEvidence> findByTenantIdAndInvestigationIdOrderByRefAsc(UUID tenantId, UUID investigationId);
    Optional<AuditInvestigationEvidence> findByTenantIdAndInvestigationIdAndId(UUID tenantId, UUID investigationId, UUID id);
    long countByTenantIdAndInvestigationId(UUID tenantId, UUID investigationId);
}
