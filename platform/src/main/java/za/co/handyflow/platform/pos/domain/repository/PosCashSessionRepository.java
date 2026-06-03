package za.co.handyflow.platform.pos.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.pos.domain.model.PosCashSession;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;
import java.util.UUID;

public interface PosCashSessionRepository extends JpaRepository<PosCashSession, UUID> {

    /** The currently open session for a tenant (there should only ever be one). */
    @Query("SELECT s FROM PosCashSession s WHERE s.tenantId = :tenantId AND s.status = 'OPEN'")
    Optional<PosCashSession> findOpenSession(TenantId tenantId);

    Optional<PosCashSession> findByIdAndTenantId(UUID id, TenantId tenantId);

    @Query("SELECT s FROM PosCashSession s WHERE s.tenantId = :tenantId ORDER BY s.openedAt DESC")
    Page<PosCashSession> findAll(TenantId tenantId, Pageable pageable);

    @Query("SELECT COALESCE(MAX(CAST(SUBSTRING(s.sessionNumber, 4) AS int)), 0) FROM PosCashSession s WHERE s.tenantId = :tenantId")
    int findMaxSessionSequence(TenantId tenantId);
}
