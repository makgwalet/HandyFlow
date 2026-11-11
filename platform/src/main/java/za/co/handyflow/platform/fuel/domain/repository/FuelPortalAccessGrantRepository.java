package za.co.handyflow.platform.fuel.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.fuel.domain.model.FuelPortalAccessGrant;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FuelPortalAccessGrantRepository extends JpaRepository<FuelPortalAccessGrant, UUID> {

    Optional<FuelPortalAccessGrant> findByInviteToken(String inviteToken);

    @Query("SELECT g FROM FuelPortalAccessGrant g WHERE g.tenantId = :tenantId AND g.accountId = :accountId ORDER BY g.invitedAt DESC")
    List<FuelPortalAccessGrant> findByTenantAndAccount(@Param("tenantId") UUID tenantId, @Param("accountId") UUID accountId);

    @Query("SELECT g FROM FuelPortalAccessGrant g WHERE g.tenantId = :tenantId AND g.id = :id")
    Optional<FuelPortalAccessGrant> findByTenantIdAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    @Query("SELECT g FROM FuelPortalAccessGrant g WHERE g.portalUserId = :portalUserId AND g.status = 'ACTIVE'")
    List<FuelPortalAccessGrant> findActiveGrantsForUser(@Param("portalUserId") UUID portalUserId);

    @Query("SELECT g FROM FuelPortalAccessGrant g WHERE g.portalUserId = :portalUserId AND g.accountId = :accountId AND g.status = 'ACTIVE'")
    Optional<FuelPortalAccessGrant> findActiveGrant(@Param("portalUserId") UUID portalUserId, @Param("accountId") UUID accountId);
}
