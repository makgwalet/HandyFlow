package za.co.handyflow.platform.warehousing.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.warehousing.domain.model.WhsePortalAccessGrant;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WhsePortalAccessGrantRepository extends JpaRepository<WhsePortalAccessGrant, UUID> {

    @Query("SELECT g FROM WhsePortalAccessGrant g WHERE g.tenantId = :tenantId AND g.id = :id")
    Optional<WhsePortalAccessGrant> findByTenantIdAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    @Query("SELECT g FROM WhsePortalAccessGrant g WHERE g.tenantId = :tenantId AND g.clientId = :clientId ORDER BY g.invitedAt DESC")
    List<WhsePortalAccessGrant> findByTenantAndClient(@Param("tenantId") UUID tenantId, @Param("clientId") UUID clientId);

    @Query("SELECT g FROM WhsePortalAccessGrant g WHERE g.inviteToken = :token")
    Optional<WhsePortalAccessGrant> findByInviteToken(@Param("token") String token);

    @Query("SELECT g FROM WhsePortalAccessGrant g WHERE g.portalUserId = :portalUserId AND g.status = 'ACTIVE'")
    List<WhsePortalAccessGrant> findActiveGrantsForUser(@Param("portalUserId") UUID portalUserId);

    @Query("SELECT g FROM WhsePortalAccessGrant g WHERE g.portalUserId = :portalUserId AND g.clientId = :clientId AND g.status = 'ACTIVE'")
    Optional<WhsePortalAccessGrant> findActiveGrant(@Param("portalUserId") UUID portalUserId, @Param("clientId") UUID clientId);
}
