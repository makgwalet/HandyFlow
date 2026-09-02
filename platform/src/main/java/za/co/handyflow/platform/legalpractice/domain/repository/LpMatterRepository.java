package za.co.handyflow.platform.legalpractice.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.legalpractice.domain.model.LpMatter;
import za.co.handyflow.platform.shared.TenantId;

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
}
