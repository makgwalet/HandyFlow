// security/domain/repository/CameraRepository.java

package za.co.handyflow.platform.security.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.security.domain.model.Camera;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CameraRepository extends JpaRepository<Camera, UUID> {

    @Query("""
        SELECT c FROM Camera c
        WHERE c.tenantId = :tenantId
        AND c.id = :id
        """)
    Optional<Camera> findByTenantAndId(TenantId tenantId, UUID id);

    @Query("""
        SELECT c FROM Camera c
        WHERE c.tenantId = :tenantId
        AND c.siteId = :siteId
        AND c.status != 'DECOMMISSIONED'
        ORDER BY c.name
        """)
    List<Camera> findActiveBySite(TenantId tenantId, UUID siteId);

    /**
     * Tenant-wide paginated list (non-decommissioned), newest-first by name —
     * backs GET /cameras, added to close the audit gap: CctvTab.tsx tries
     * "/cameras/site/all" then falls back to "/cameras?size=100", but
     * CameraController only ever exposed /{id} and /site/{siteId} — no
     * tenant-wide list existed, so both attempts 404'd and the CCTV registry
     * tab silently rendered empty. The tab's existing fallback already
     * targets this exact URL, so no frontend change is needed — this query
     * (and the controller/service methods that use it) just needed to exist.
     */
    @Query("""
        SELECT c FROM Camera c
        WHERE c.tenantId = :tenantId
        AND c.status != 'DECOMMISSIONED'
        ORDER BY c.name
        """)
    Page<Camera> findAllActive(TenantId tenantId, Pageable pageable);
}