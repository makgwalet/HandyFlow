package za.co.handyflow.platform.internalaudit.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.co.handyflow.platform.internalaudit.domain.model.AuditInvestigationMember;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AuditInvestigationMemberRepository extends JpaRepository<AuditInvestigationMember, UUID> {
    List<AuditInvestigationMember> findByTenantIdAndInvestigationId(UUID tenantId, UUID investigationId);
    Optional<AuditInvestigationMember> findByTenantIdAndInvestigationIdAndUserIdAndRemovedAtIsNull(UUID tenantId, UUID investigationId, UUID userId);
    List<AuditInvestigationMember> findByTenantIdAndUserIdAndRemovedAtIsNull(UUID tenantId, UUID userId);
}
