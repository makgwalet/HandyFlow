package za.co.handyflow.platform.legalpractice.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.legalpractice.domain.model.LpTrustTransaction;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;
import java.util.UUID;

/**
 * {@link LpTrustTransaction} is append-only (no {@code @Version}, no
 * update methods) — this repository only ever inserts and reads, never
 * updates a row in place, matching the entity's own compliance intent
 * (a trust ledger row is a permanent record of a real money movement).
 */
public interface LpTrustTransactionRepository extends JpaRepository<LpTrustTransaction, UUID> {

    @Query("SELECT t FROM LpTrustTransaction t WHERE t.tenantId = :tenantId AND t.id = :id")
    Optional<LpTrustTransaction> findActiveById(TenantId tenantId, UUID id);

    @Query("""
        SELECT t FROM LpTrustTransaction t
        WHERE t.tenantId = :tenantId AND t.clientId = :clientId
        ORDER BY t.transactionDate DESC, t.createdAt DESC
        """)
    Page<LpTrustTransaction> findByClient(TenantId tenantId, UUID clientId, Pageable pageable);
}
