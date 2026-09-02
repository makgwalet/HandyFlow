package za.co.handyflow.platform.collectionsagency.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.collectionsagency.domain.model.CollAgencyPortalAccessGrant;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CollAgencyPortalAccessGrantRepository extends JpaRepository<CollAgencyPortalAccessGrant, UUID> {

    @Query("SELECT g FROM CollAgencyPortalAccessGrant g WHERE g.tenantId = :tenantId AND g.id = :id")
    Optional<CollAgencyPortalAccessGrant> findByTenantIdAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    @Query("SELECT g FROM CollAgencyPortalAccessGrant g WHERE g.tenantId = :tenantId AND g.clientId = :clientId ORDER BY g.invitedAt DESC")
    List<CollAgencyPortalAccessGrant> findByTenantAndClient(@Param("tenantId") UUID tenantId, @Param("clientId") UUID clientId);

    @Query("SELECT g FROM CollAgencyPortalAccessGrant g WHERE g.inviteToken = :token")
    Optional<CollAgencyPortalAccessGrant> findByInviteToken(@Param("token") String token);

    @Query("SELECT g FROM CollAgencyPortalAccessGrant g WHERE g.portalUserId = :portalUserId AND g.status = 'ACTIVE'")
    List<CollAgencyPortalAccessGrant> findActiveGrantsForUser(@Param("portalUserId") UUID portalUserId);

    @Query("SELECT g FROM CollAgencyPortalAccessGrant g WHERE g.portalUserId = :portalUserId AND g.clientId = :clientId AND g.status = 'ACTIVE'")
    Optional<CollAgencyPortalAccessGrant> findActiveGrant(@Param("portalUserId") UUID portalUserId, @Param("clientId") UUID clientId);
}
