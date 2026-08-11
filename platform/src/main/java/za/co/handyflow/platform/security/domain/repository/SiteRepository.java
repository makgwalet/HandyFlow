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

    @Query("SELECT s FROM Site s LEFT JOIN FETCH s.checkpoints WHERE s.portalToken = :token AND s.portalEnabled = true AND s.deletedAt IS NULL")
    Optional<Site> findByPortalToken(String token);

    @Query(value = "SELECT DISTINCT tenant_id FROM security_guards WHERE deleted_at IS NULL", nativeQuery = true)
    List<UUID> findDistinctActiveTenantIds();

    /**
     * Branch-scoped list (V218) -- ready for the future enforcement layer
     * (not yet wired into any controller; see BranchController's
     * ENFORCEMENT NOTE and Site.branchId's javadoc for what's still
     * missing before this can actually be used to restrict a regional
     * manager's visibility). Available now so that work doesn't also need
     * a new repository method added at the same time.
     */
    @Query("""
        SELECT s FROM Site s
        WHERE s.tenantId = :tenantId
        AND s.branchId = :branchId
        AND s.deletedAt IS NULL
        ORDER BY s.name
        """)
    Page<Site> findAllActiveByBranch(TenantId tenantId, UUID branchId, Pageable pageable);
}