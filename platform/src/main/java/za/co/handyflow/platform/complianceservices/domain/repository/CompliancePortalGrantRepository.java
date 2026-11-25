package za.co.handyflow.platform.complianceservices.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.complianceservices.domain.model.CompliancePortalGrant;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CompliancePortalGrantRepository extends JpaRepository<CompliancePortalGrant, UUID> {

    Optional<CompliancePortalGrant> findByInviteToken(String inviteToken);

    /** What a signed-in portal user may see. Not tenant-scoped: a portal login is not tied to one tenant, so each grant carries its own. */
    @Query("SELECT g FROM CompliancePortalGrant g WHERE g.portalUserId = :portalUserId AND g.status = 'ACTIVE'")
    List<CompliancePortalGrant> findActiveForUser(@Param("portalUserId") UUID portalUserId);

    @Query("SELECT g FROM CompliancePortalGrant g WHERE g.tenantId = :#{#tenantId.value} AND g.clientId = :clientId ORDER BY g.invitedAt DESC")
    List<CompliancePortalGrant> findForClient(@Param("tenantId") TenantId tenantId, @Param("clientId") UUID clientId);

    @Query("SELECT g FROM CompliancePortalGrant g WHERE g.tenantId = :#{#tenantId.value} AND g.id = :id")
    Optional<CompliancePortalGrant> findOne(@Param("tenantId") TenantId tenantId, @Param("id") UUID id);

    @Query("SELECT g FROM CompliancePortalGrant g WHERE g.tenantId = :#{#tenantId.value} AND g.clientId = :clientId AND lower(g.inviteEmail) = :email AND g.status <> 'REVOKED'")
    Optional<CompliancePortalGrant> findLive(@Param("tenantId") TenantId tenantId, @Param("clientId") UUID clientId, @Param("email") String email);
}
