package za.co.handyflow.platform.legalpractice.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;
import java.util.UUID;
import za.co.handyflow.platform.legalpractice.domain.model.LpConflictCheck;

public interface LpConflictCheckRepository extends JpaRepository<LpConflictCheck, UUID> {

    @Query("SELECT c FROM LpConflictCheck c WHERE c.tenantId = :tenantId AND c.id = :id")
    Optional<LpConflictCheck> findActiveById(TenantId tenantId, UUID id);

    @Query("""
        SELECT c FROM LpConflictCheck c WHERE c.tenantId = :tenantId
        AND (:status IS NULL OR c.status = :status)
        AND (:clientId IS NULL OR c.clientId = :clientId)
        ORDER BY c.runAt DESC
        """)
    Page<LpConflictCheck> search(TenantId tenantId, String status, UUID clientId, Pageable pageable);
}
