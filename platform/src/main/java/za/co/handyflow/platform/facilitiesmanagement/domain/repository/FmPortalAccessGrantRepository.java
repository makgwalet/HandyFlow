package za.co.handyflow.platform.facilitiesmanagement.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmPortalAccessGrant;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FmPortalAccessGrantRepository extends JpaRepository<FmPortalAccessGrant, UUID> {

    Optional<FmPortalAccessGrant> findByInviteToken(String inviteToken);

    /**
     * A portal user's own ACTIVE grant(s) — used by {@code FmPortalDataService}
     * to resolve which {@code clientId} a logged-in portal user may see.
     * {@code FmPortalAccessGrant.tenantId} is a raw UUID column (see the
     * entity's own Javadoc), so the tenant filter here compares UUID to UUID
     * rather than going through the TenantId embeddable's own attribute path.
     */
    @Query("SELECT g FROM FmPortalAccessGrant g WHERE g.portalUserId = :portalUserId AND g.status = 'ACTIVE'")
    List<FmPortalAccessGrant> findByPortalUserId(@Param("portalUserId") UUID portalUserId);

    @Query("SELECT g FROM FmPortalAccessGrant g WHERE g.tenantId = :#{#tenantId.value} AND g.clientId = :clientId ORDER BY g.invitedAt DESC")
    List<FmPortalAccessGrant> findAllForClient(@Param("tenantId") TenantId tenantId, @Param("clientId") UUID clientId);

    @Query("SELECT g FROM FmPortalAccessGrant g WHERE g.tenantId = :#{#tenantId.value} AND g.id = :id")
    Optional<FmPortalAccessGrant> findByTenantAndId(@Param("tenantId") TenantId tenantId, @Param("id") UUID id);
}
