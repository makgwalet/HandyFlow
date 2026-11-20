package za.co.handyflow.platform.legalpractice.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.legalpractice.domain.model.LpMatter;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Tenant-scoped matters, with the two listing shapes the billing/portfolio
 * screens need: every matter for one client, and every matter for the firm
 * as a whole.
 */
public interface LpMatterRepository extends JpaRepository<LpMatter, UUID> {

    @Query("SELECT m FROM LpMatter m WHERE m.tenantId = :tenantId AND m.id = :id")
    Optional<LpMatter> findActiveById(TenantId tenantId, UUID id);

    @Query("""
        SELECT m FROM LpMatter m
        WHERE m.tenantId = :tenantId AND m.clientId = :clientId
        ORDER BY m.createdAt DESC
        """)
    Page<LpMatter> findAllActiveForClient(TenantId tenantId, UUID clientId, Pageable pageable);

    @Query("SELECT m FROM LpMatter m WHERE m.tenantId = :tenantId ORDER BY m.createdAt DESC")
    Page<LpMatter> findAllActiveForFirm(TenantId tenantId, Pageable pageable);

    /** The firm's matters, leaving out the ones a wall bars the signed-in user from (matters walled directly, and every matter of a walled client). */
    @Query("""
        SELECT m FROM LpMatter m
        WHERE m.tenantId = :tenantId AND m.id NOT IN :barredMatterIds AND m.clientId NOT IN :barredClientIds
        ORDER BY m.createdAt DESC
        """)
    Page<LpMatter> findAllActiveForFirmExcluding(TenantId tenantId, List<UUID> barredMatterIds, List<UUID> barredClientIds, Pageable pageable);

    @Query("""
        SELECT m FROM LpMatter m
        WHERE m.tenantId = :tenantId AND m.clientId = :clientId AND m.id NOT IN :barredMatterIds
        ORDER BY m.createdAt DESC
        """)
    Page<LpMatter> findAllActiveForClientExcluding(TenantId tenantId, UUID clientId, List<UUID> barredMatterIds, Pageable pageable);
}
