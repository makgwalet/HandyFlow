package za.co.handyflow.platform.legalpractice.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.legalpractice.domain.model.LpPortalAccessGrant;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * {@link LpPortalAccessGrant} now uses the confirmed-real invite-token/
 * email/status shape (see that entity's own class Javadoc) — every finder
 * here works either from an invite token (pre-registration, no
 * {@code PortalUser} needs to exist yet) or an already-resolved
 * {@code portalUserId} (post-acceptance), matching
 * {@code AccPortalAccessGrantRepository}/{@code AuditorAccessGrantRepository}'s
 * own confirmed shape.
 */
public interface LpPortalAccessGrantRepository extends JpaRepository<LpPortalAccessGrant, UUID> {

    /** Redeeming an invite link — backs {@code LpPortalAuthService.registerViaInvite()}. */
    Optional<LpPortalAccessGrant> findByInviteToken(String inviteToken);

    /** Tenant-safe single-grant lookup — backs revoke(), matching this codebase's own established convention. */
    @Query("SELECT g FROM LpPortalAccessGrant g WHERE g.tenantId = :tenantId AND g.id = :id")
    Optional<LpPortalAccessGrant> findActiveById(TenantId tenantId, UUID id);

    /** Staff-side view: every invite/grant (pending, active, or revoked) issued for this client. */
    @Query("""
        SELECT g FROM LpPortalAccessGrant g
        WHERE g.tenantId = :tenantId AND g.clientId = :clientId
        ORDER BY g.invitedAt DESC
        """)
    List<LpPortalAccessGrant> findAllForClient(TenantId tenantId, UUID clientId);

    /**
     * The single query every portal-data endpoint depends on to enforce
     * isolation: "does this portal user have ACTIVE access to this
     * specific client?" Deliberately checks {@code status = 'ACTIVE'}, not
     * merely {@code revokedAt IS NULL} — a still-PENDING (unaccepted)
     * invite must never authorize a read.
     */
    @Query("""
        SELECT g FROM LpPortalAccessGrant g
        WHERE g.portalUserId = :portalUserId AND g.clientId = :clientId AND g.status = 'ACTIVE'
        """)
    Optional<LpPortalAccessGrant> findActiveGrant(UUID portalUserId, UUID clientId);

    /** All clients a portal user currently has active access to. */
    @Query("""
        SELECT g FROM LpPortalAccessGrant g
        WHERE g.portalUserId = :portalUserId AND g.status = 'ACTIVE'
        """)
    List<LpPortalAccessGrant> findAllActiveForPortalUser(UUID portalUserId);
}
