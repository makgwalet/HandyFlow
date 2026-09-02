package za.co.handyflow.platform.bookkeeping.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.bookkeeping.domain.model.BkPortalAccessGrant;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BkPortalAccessGrantRepository extends JpaRepository<BkPortalAccessGrant, UUID> {

    Optional<BkPortalAccessGrant> findByInviteToken(String inviteToken);

    /**
     * A portal user's own ACTIVE grant(s) — used by {@code BkPortalDataService}
     * to resolve which {@code clientId} a logged-in portal user may see.
     * {@code BkPortalAccessGrant.tenantId} is a raw UUID column (see the
     * entity's own Javadoc), so the tenant filter here compares UUID to UUID
     * rather than going through the TenantId embeddable's own attribute path.
     */
    @Query("SELECT g FROM BkPortalAccessGrant g WHERE g.portalUserId = :portalUserId AND g.status = 'ACTIVE'")
    List<BkPortalAccessGrant> findByPortalUserId(@Param("portalUserId") UUID portalUserId);

    @Query("SELECT g FROM BkPortalAccessGrant g WHERE g.tenantId = :#{#tenantId.value} AND g.clientId = :clientId ORDER BY g.invitedAt DESC")
    List<BkPortalAccessGrant> findAllForClient(@Param("tenantId") TenantId tenantId, @Param("clientId") UUID clientId);

    @Query("SELECT g FROM BkPortalAccessGrant g WHERE g.tenantId = :#{#tenantId.value} AND g.id = :id")
    Optional<BkPortalAccessGrant> findByTenantAndId(@Param("tenantId") TenantId tenantId, @Param("id") UUID id);
}
