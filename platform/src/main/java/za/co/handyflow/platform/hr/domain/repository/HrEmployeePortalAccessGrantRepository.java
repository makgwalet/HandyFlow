package za.co.handyflow.platform.hr.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.hr.domain.model.HrEmployeePortalAccessGrant;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HrEmployeePortalAccessGrantRepository extends JpaRepository<HrEmployeePortalAccessGrant, UUID> {

    Optional<HrEmployeePortalAccessGrant> findByInviteToken(String inviteToken);

    @Query("""
        SELECT g FROM HrEmployeePortalAccessGrant g
        WHERE g.portalUserId = :portalUserId AND g.employeeId = :employeeId AND g.status = 'ACTIVE'
    """)
    Optional<HrEmployeePortalAccessGrant> findActiveGrant(@Param("portalUserId") UUID portalUserId,
                                                          @Param("employeeId") UUID employeeId);

    @Query("SELECT g FROM HrEmployeePortalAccessGrant g WHERE g.portalUserId = :portalUserId AND g.status = 'ACTIVE'")
    List<HrEmployeePortalAccessGrant> findActiveGrantsForUser(@Param("portalUserId") UUID portalUserId);

    @Query("SELECT g FROM HrEmployeePortalAccessGrant g WHERE g.tenantId = :tenantId AND g.id = :id")
    Optional<HrEmployeePortalAccessGrant> findByTenantIdAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    @Query("""
        SELECT g FROM HrEmployeePortalAccessGrant g
        WHERE g.tenantId = :tenantId AND g.employeeId = :employeeId
        ORDER BY g.invitedAt DESC
    """)
    List<HrEmployeePortalAccessGrant> findByTenantAndEmployee(@Param("tenantId") UUID tenantId,
                                                              @Param("employeeId") UUID employeeId);
}