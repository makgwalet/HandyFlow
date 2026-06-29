// security/domain/repository/SiteRepository.java

package za.co.handyflow.platform.security.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.security.domain.model.Site;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SiteRepository extends JpaRepository<Site, UUID> {

    @Query("SELECT s FROM Site s WHERE s.tenantId = :tenantId AND s.deletedAt IS NULL ORDER BY s.name")
    Page<Site> findAllActive(TenantId tenantId, Pageable pageable);

    @Query("SELECT s FROM Site s LEFT JOIN FETCH s.checkpoints WHERE s.tenantId = :tenantId AND s.id = :id AND s.deletedAt IS NULL")
    Optional<Site> findActiveByIdWithCheckpoints(TenantId tenantId, UUID id);

    @Query("SELECT s FROM Site s WHERE s.tenantId = :tenantId AND s.id = :id AND s.deletedAt IS NULL")
    Optional<Site> findActiveById(TenantId tenantId, UUID id);

    /**
     * Client portal lookup — finds a site by its portal token.
     *
     * WHY no tenantId filter?
     * Portal tokens are the authentication mechanism — they ARE the tenant
     * identification.  The token is globally unique (UUID, indexed) so we
     * don't need a tenant scope.  The service validates that the site is active
     * and the portal is enabled before returning data.
     *
     * This endpoint is called without an authenticated session (the client
     * accesses the portal via a public URL with only the token).
     */
    @Query("SELECT s FROM Site s LEFT JOIN FETCH s.checkpoints WHERE s.portalToken = :token AND s.portalEnabled = true AND s.deletedAt IS NULL")
    Optional<Site> findByPortalToken(String token);

    /**
     * Used by PSiRA compliance scheduler to iterate tenants without loading all guards.
     * Returns distinct tenant UUIDs that have at least one non-deleted guard.
     */
    @Query(value = "SELECT DISTINCT tenant_id FROM security_guards WHERE deleted_at IS NULL", nativeQuery = true)
    List<UUID> findDistinctActiveTenantIds();
}
