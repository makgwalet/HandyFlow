package za.co.handyflow.platform.legalpractice.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.legalpractice.domain.model.LpEthicalWall;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LpEthicalWallRepository extends JpaRepository<LpEthicalWall, UUID> {

    @Query("SELECT w FROM LpEthicalWall w WHERE w.tenantId = :tenantId AND w.id = :id")
    Optional<LpEthicalWall> findActiveById(TenantId tenantId, UUID id);

    @Query("""
        SELECT w FROM LpEthicalWall w
        WHERE w.tenantId = :tenantId AND (:activeOnly = false OR w.liftedAt IS NULL)
        ORDER BY w.createdAt DESC
        """)
    Page<LpEthicalWall> list(TenantId tenantId, boolean activeOnly, Pageable pageable);

    /** Every wall in force against this person. */
    @Query("SELECT w FROM LpEthicalWall w WHERE w.tenantId = :tenantId AND w.userId = :userId AND w.liftedAt IS NULL")
    List<LpEthicalWall> findInForceForUser(TenantId tenantId, UUID userId);

    @Query("SELECT COUNT(w) FROM LpEthicalWall w WHERE w.tenantId = :tenantId AND w.userId = :userId AND w.matterId = :matterId AND w.liftedAt IS NULL")
    long countInForceOnMatter(TenantId tenantId, UUID userId, UUID matterId);

    @Query("SELECT COUNT(w) FROM LpEthicalWall w WHERE w.tenantId = :tenantId AND w.userId = :userId AND w.clientId = :clientId AND w.liftedAt IS NULL")
    long countInForceOnClient(TenantId tenantId, UUID userId, UUID clientId);
}
