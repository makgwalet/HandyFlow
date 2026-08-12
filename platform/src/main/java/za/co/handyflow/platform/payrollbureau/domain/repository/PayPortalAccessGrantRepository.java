package za.co.handyflow.platform.payrollbureau.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.payrollbureau.domain.model.PayPortalAccessGrant;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PayPortalAccessGrantRepository extends JpaRepository<PayPortalAccessGrant, UUID> {

    Optional<PayPortalAccessGrant> findByInviteToken(String inviteToken);

    @Query("""
        SELECT g FROM PayrollBureauPortalAccessGrant g
        WHERE g.portalUserId = :portalUserId AND g.payClientId = :clientId AND g.status = 'ACTIVE'
    """)
    Optional<PayPortalAccessGrant> findActiveGrant(@Param("portalUserId") UUID portalUserId,
                                                   @Param("clientId") UUID clientId);

    @Query("SELECT g FROM PayrollBureauPortalAccessGrant g WHERE g.portalUserId = :portalUserId AND g.status = 'ACTIVE'")
    List<PayPortalAccessGrant> findActiveGrantsForUser(@Param("portalUserId") UUID portalUserId);

    @Query("SELECT g FROM PayrollBureauPortalAccessGrant g WHERE g.tenantId = :tenantId AND g.id = :id")
    Optional<PayPortalAccessGrant> findByTenantIdAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    @Query("""
        SELECT g FROM PayrollBureauPortalAccessGrant g
        WHERE g.tenantId = :tenantId AND g.payClientId = :clientId
        ORDER BY g.invitedAt DESC
    """)
    List<PayPortalAccessGrant> findByTenantAndClient(@Param("tenantId") UUID tenantId,
                                                     @Param("clientId") UUID clientId);
}