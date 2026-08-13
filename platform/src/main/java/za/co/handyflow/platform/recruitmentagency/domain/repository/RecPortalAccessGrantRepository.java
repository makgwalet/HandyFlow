package za.co.handyflow.platform.recruitmentagency.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.recruitmentagency.domain.model.RecPortalAccessGrant;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RecPortalAccessGrantRepository extends JpaRepository<RecPortalAccessGrant, UUID> {

    Optional<RecPortalAccessGrant> findByInviteToken(String inviteToken);

    @Query("""
        SELECT g FROM RecruitmentAgencyPortalAccessGrant g
        WHERE g.portalUserId = :portalUserId AND g.clientId = :clientId AND g.status = 'ACTIVE'
    """)
    Optional<RecPortalAccessGrant> findActiveGrant(@Param("portalUserId") UUID portalUserId,
                                                   @Param("clientId") UUID clientId);

    @Query("SELECT g FROM RecruitmentAgencyPortalAccessGrant g WHERE g.portalUserId = :portalUserId AND g.status = 'ACTIVE'")
    List<RecPortalAccessGrant> findActiveGrantsForUser(@Param("portalUserId") UUID portalUserId);

    @Query("SELECT g FROM RecruitmentAgencyPortalAccessGrant g WHERE g.tenantId = :tenantId AND g.id = :id")
    Optional<RecPortalAccessGrant> findByTenantIdAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    @Query("""
        SELECT g FROM RecruitmentAgencyPortalAccessGrant g
        WHERE g.tenantId = :tenantId AND g.clientId = :clientId
        ORDER BY g.invitedAt DESC
    """)
    List<RecPortalAccessGrant> findByTenantAndClient(@Param("tenantId") UUID tenantId, @Param("clientId") UUID clientId);
}