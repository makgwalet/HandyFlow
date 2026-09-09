package za.co.handyflow.platform.property.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.property.domain.model.PropPortalAccessGrant;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PropPortalAccessGrantRepository extends JpaRepository<PropPortalAccessGrant, UUID> {

    @Query("SELECT g FROM PropPortalAccessGrant g WHERE g.tenantId = :tenantId AND g.id = :id")
    Optional<PropPortalAccessGrant> findByTenantIdAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    @Query("SELECT g FROM PropPortalAccessGrant g WHERE g.tenantId = :tenantId AND g.leaseId = :leaseId ORDER BY g.invitedAt DESC")
    List<PropPortalAccessGrant> findByTenantAndLease(@Param("tenantId") UUID tenantId, @Param("leaseId") UUID leaseId);

    @Query("SELECT g FROM PropPortalAccessGrant g WHERE g.inviteToken = :token")
    Optional<PropPortalAccessGrant> findByInviteToken(@Param("token") String token);

    // FIX (agreed design decision 1: "query all leases") — every active
    // grant for this portal user, across every lease they've ever been
    // invited to (renewed leases, more than one unit, etc.). The data
    // service uses this to decide whether to show a single lease
    // straight away or a list to choose from.
    @Query("SELECT g FROM PropPortalAccessGrant g WHERE g.portalUserId = :portalUserId AND g.status = 'ACTIVE'")
    List<PropPortalAccessGrant> findActiveGrantsForUser(@Param("portalUserId") UUID portalUserId);

    @Query("SELECT g FROM PropPortalAccessGrant g WHERE g.portalUserId = :portalUserId AND g.leaseId = :leaseId AND g.status = 'ACTIVE'")
    Optional<PropPortalAccessGrant> findActiveGrant(@Param("portalUserId") UUID portalUserId, @Param("leaseId") UUID leaseId);
}
