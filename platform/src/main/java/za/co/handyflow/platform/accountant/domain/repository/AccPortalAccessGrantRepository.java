package za.co.handyflow.platform.accountant.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import za.co.handyflow.platform.accountant.domain.model.AccPortalAccessGrant;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccPortalAccessGrantRepository extends JpaRepository<AccPortalAccessGrant, UUID> {

    Optional<AccPortalAccessGrant> findByInviteToken(String inviteToken);

    /**
     * The single query every accountant-portal endpoint depends on to
     * enforce isolation: "does this portal user have ACTIVE access to
     * this specific client?" Kept as one shared, reusable query rather
     * than reimplemented per-endpoint — see AccPortalAccessGrant's own
     * class Javadoc for why that matters.
     */
    @Query("""
        SELECT g FROM AccountantPortalAccessGrant g
        WHERE g.portalUserId = :portalUserId
          AND g.clientId = :clientId
          AND g.status = 'ACTIVE'
    """)
    Optional<AccPortalAccessGrant> findActiveGrant(@Param("portalUserId") UUID portalUserId,
                                                   @Param("clientId") UUID clientId);

    /** All clients a portal user currently has active access to — backs their own "my clients" view. */
    @Query("""
        SELECT g FROM AccountantPortalAccessGrant g
        WHERE g.portalUserId = :portalUserId
          AND g.status = 'ACTIVE'
    """)
    List<AccPortalAccessGrant> findActiveGrantsForUser(@Param("portalUserId") UUID portalUserId);

    /** Staff-side view: who has portal access (or a pending invite) to this client. */
    @Query("""
        SELECT g FROM AccountantPortalAccessGrant g
        WHERE g.tenantId = :tenantId
          AND g.clientId = :clientId
        ORDER BY g.invitedAt DESC
    """)
    List<AccPortalAccessGrant> findByTenantAndClient(@Param("tenantId") UUID tenantId,
                                                     @Param("clientId") UUID clientId);

    /**
     * Tenant-safe single-grant lookup — backs revoke(), matching this
     * module's own established convention (FeeNoteRepository/
     * AccJournalRepository/TimeEntryRepository all have the equivalent)
     * rather than a raw findById() with manual comparison after.
     */
    @Query("""
        SELECT g FROM AccountantPortalAccessGrant g
        WHERE g.tenantId = :tenantId
          AND g.id = :id
    """)
    Optional<AccPortalAccessGrant> findByTenantIdAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);
}