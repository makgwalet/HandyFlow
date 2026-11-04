package za.co.handyflow.platform.projects.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.projects.domain.model.ProjectPortalGrant;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectPortalGrantRepository extends JpaRepository<ProjectPortalGrant, UUID> {

    @Query("SELECT g FROM ProjectPortalGrant g WHERE g.inviteTokenHash = :hash")
    Optional<ProjectPortalGrant> findByInviteTokenHash(@Param("hash") String hash);

    @Query("SELECT g FROM ProjectPortalGrant g WHERE g.portalUserId = :userId AND g.status = 'ACTIVE'")
    List<ProjectPortalGrant> findActiveForUser(@Param("userId") UUID portalUserId);

    @Query("SELECT g FROM ProjectPortalGrant g WHERE g.tenantId = :tenantId ORDER BY g.invitedAt DESC")
    List<ProjectPortalGrant> findByTenant(@Param("tenantId") UUID tenantId);

    @Query("SELECT g FROM ProjectPortalGrant g WHERE g.tenantId = :tenantId AND g.id = :id")
    Optional<ProjectPortalGrant> findByTenantAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);
}
