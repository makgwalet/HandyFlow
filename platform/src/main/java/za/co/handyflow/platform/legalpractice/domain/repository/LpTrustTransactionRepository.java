package za.co.handyflow.platform.legalpractice.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.legalpractice.domain.model.LpTrustTransaction;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
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

    @Query("SELECT t FROM LpTrustTransaction t WHERE t.tenantId = :tenantId AND t.idempotencyKey = :key")
    Optional<LpTrustTransaction> findByIdempotencyKey(TenantId tenantId, String key);

    /** Receipts minus everything paid out, for the movements tagged to this matter. Native so the sum is always a decimal, never a rounded integer. */
    @Query(value = """
        SELECT COALESCE(SUM(CASE WHEN transaction_type = 'RECEIPT' THEN amount ELSE -amount END), 0)
        FROM lp_trust_transactions WHERE tenant_id = :tenantId AND matter_id = :matterId
        """, nativeQuery = true)
    java.math.BigDecimal netHeldForMatterRaw(@org.springframework.data.repository.query.Param("tenantId") UUID tenantId,
                                             @org.springframework.data.repository.query.Param("matterId") UUID matterId);

    default java.math.BigDecimal netHeldForMatter(TenantId tenantId, UUID matterId) {
        return netHeldForMatterRaw(tenantId.getValue(), matterId);
    }

    /** A client's ledger, leaving out rows on matters the signed-in user is walled from. Rows with no matter stay. */
    @Query("""
        SELECT t FROM LpTrustTransaction t
        WHERE t.tenantId = :tenantId AND t.clientId = :clientId
        AND (t.matterId IS NULL OR t.matterId NOT IN :barredMatterIds)
        ORDER BY t.transactionDate DESC, t.createdAt DESC
        """)
    Page<LpTrustTransaction> findByClientExcluding(TenantId tenantId, UUID clientId, List<UUID> barredMatterIds, Pageable pageable);
}
