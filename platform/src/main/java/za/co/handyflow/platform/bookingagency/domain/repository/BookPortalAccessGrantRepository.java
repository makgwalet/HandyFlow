package za.co.handyflow.platform.bookingagency.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.bookingagency.domain.model.BookPortalAccessGrant;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookPortalAccessGrantRepository extends JpaRepository<BookPortalAccessGrant, UUID> {

    Optional<BookPortalAccessGrant> findByInviteToken(String inviteToken);

    @Query("""
        SELECT g FROM BookingAgencyPortalAccessGrant g
        WHERE g.portalUserId = :portalUserId AND g.clientId = :clientId AND g.status = 'ACTIVE'
    """)
    Optional<BookPortalAccessGrant> findActiveGrant(@Param("portalUserId") UUID portalUserId,
                                                    @Param("clientId") UUID clientId);

    @Query("SELECT g FROM BookingAgencyPortalAccessGrant g WHERE g.portalUserId = :portalUserId AND g.status = 'ACTIVE'")
    List<BookPortalAccessGrant> findActiveGrantsForUser(@Param("portalUserId") UUID portalUserId);

    @Query("SELECT g FROM BookingAgencyPortalAccessGrant g WHERE g.tenantId = :tenantId AND g.id = :id")
    Optional<BookPortalAccessGrant> findByTenantIdAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    @Query("""
        SELECT g FROM BookingAgencyPortalAccessGrant g
        WHERE g.tenantId = :tenantId AND g.clientId = :clientId
        ORDER BY g.invitedAt DESC
    """)
    List<BookPortalAccessGrant> findByTenantAndClient(@Param("tenantId") UUID tenantId, @Param("clientId") UUID clientId);
}