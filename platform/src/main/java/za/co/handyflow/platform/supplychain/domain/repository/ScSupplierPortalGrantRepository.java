package za.co.handyflow.platform.supplychain.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.supplychain.domain.model.ScSupplierPortalGrant;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScSupplierPortalGrantRepository extends JpaRepository<ScSupplierPortalGrant, UUID> {

    Optional<ScSupplierPortalGrant> findByInviteToken(String inviteToken);

    @Query("SELECT g FROM ScSupplierPortalGrant g WHERE g.tenantId = :tenantId AND g.supplierId = :supplierId ORDER BY g.invitedAt DESC")
    List<ScSupplierPortalGrant> findBySupplier(@Param("tenantId") UUID tenantId, @Param("supplierId") UUID supplierId);

    @Query("SELECT g FROM ScSupplierPortalGrant g WHERE g.tenantId = :tenantId AND g.id = :id")
    Optional<ScSupplierPortalGrant> findByTenantAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    @Query("SELECT g FROM ScSupplierPortalGrant g WHERE g.portalUserId = :portalUserId AND g.status = 'ACTIVE'")
    List<ScSupplierPortalGrant> findActiveForPortalUser(@Param("portalUserId") UUID portalUserId);
}
