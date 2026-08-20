package za.co.handyflow.platform.auditor.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.co.handyflow.platform.auditor.domain.model.AuditorAccessGrant;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AuditorAccessGrantRepository extends JpaRepository<AuditorAccessGrant, UUID> {
    Optional<AuditorAccessGrant> findByInviteToken(String inviteToken);
    List<AuditorAccessGrant> findByTenantId(UUID tenantId);
    Optional<AuditorAccessGrant> findByTenantIdAndId(UUID tenantId, UUID id);
    List<AuditorAccessGrant> findByPortalUserId(UUID portalUserId);
}