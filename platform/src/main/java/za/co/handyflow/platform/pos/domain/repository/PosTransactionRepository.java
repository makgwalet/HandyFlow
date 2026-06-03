package za.co.handyflow.platform.pos.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.pos.domain.model.PosTransaction;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PosTransactionRepository extends JpaRepository<PosTransaction, UUID> {

    @Query("""
        SELECT t FROM PosTransaction t
        WHERE t.tenantId = :tenantId
        AND t.status != 'VOIDED'
        ORDER BY t.createdAt DESC
        """)
    Page<PosTransaction> findAll(TenantId tenantId, Pageable pageable);

    Optional<PosTransaction> findByIdAndTenantId(UUID id, TenantId tenantId);

    @Query("""
        SELECT COALESCE(MAX(CAST(SUBSTRING(t.transactionNumber, 5) AS int)), 0)
        FROM PosTransaction t WHERE t.tenantId = :tenantId
        """)
    int findMaxTransactionSequence(TenantId tenantId);

    // ── Financial aggregates ──────────────────────────────────────────────────

    @Query("""
        SELECT COALESCE(SUM(t.totalAmount), 0) FROM PosTransaction t
        WHERE t.tenantId = :tenantId
        AND t.status = 'COMPLETED'
        AND t.originalTransactionId IS NULL
        AND t.createdAt BETWEEN :from AND :to
        """)
    BigDecimal sumSalesBetween(TenantId tenantId, Instant from, Instant to);

    @Query("""
        SELECT COUNT(t) FROM PosTransaction t
        WHERE t.tenantId = :tenantId
        AND t.status = 'COMPLETED'
        AND t.originalTransactionId IS NULL
        AND t.createdAt BETWEEN :from AND :to
        """)
    long countSalesBetween(TenantId tenantId, Instant from, Instant to);

    // ── Cash session queries ──────────────────────────────────────────────────

    /** All COMPLETED sales (not refunds) in a session — for expected-cash calculation */
    @Query("""
        SELECT COALESCE(SUM(t.totalAmount), 0) FROM PosTransaction t
        WHERE t.cashSessionId = :sessionId
        AND t.status = 'COMPLETED'
        AND t.paymentMethod = 'CASH'
        AND t.originalTransactionId IS NULL
        """)
    BigDecimal sumCashSalesBySession(UUID sessionId);

    @Query("""
        SELECT COALESCE(SUM(t.totalAmount), 0) FROM PosTransaction t
        WHERE t.cashSessionId = :sessionId
        AND t.status = 'COMPLETED'
        AND t.originalTransactionId IS NULL
        """)
    BigDecimal sumTotalSalesBySession(UUID sessionId);

    @Query("""
        SELECT COUNT(t) FROM PosTransaction t
        WHERE t.cashSessionId = :sessionId
        AND t.status = 'COMPLETED'
        AND t.originalTransactionId IS NULL
        """)
    int countBySession(UUID sessionId);

    // ── Refund queries ────────────────────────────────────────────────────────

    /** Returns all REFUND transactions against an original sale */
    List<PosTransaction> findByOriginalTransactionIdAndTenantId(UUID originalTransactionId,
                                                                TenantId tenantId);

    // ── Z-Report payment method breakdown ────────────────────────────────────

    @Query("""
        SELECT t.paymentMethod, COUNT(t), COALESCE(SUM(t.totalAmount), 0)
        FROM PosTransaction t
        WHERE t.tenantId = :tenantId
        AND t.status = 'COMPLETED'
        AND t.originalTransactionId IS NULL
        AND t.createdAt BETWEEN :from AND :to
        GROUP BY t.paymentMethod
        """)
    List<Object[]> sumByPaymentMethodBetween(TenantId tenantId, Instant from, Instant to);

    // ── Top items (for Z-report) ──────────────────────────────────────────────

    @Query("""
        SELECT ti.itemName,
               COALESCE(SUM(ti.qty), 0),
               COALESCE(SUM(ti.lineTotal), 0)
        FROM PosTransactionItem ti
        JOIN PosTransaction t ON ti.transactionId = t.id
        WHERE t.tenantId = :tenantId
        AND t.status = 'COMPLETED'
        AND t.originalTransactionId IS NULL
        AND t.createdAt BETWEEN :from AND :to
        GROUP BY ti.itemName
        ORDER BY SUM(ti.lineTotal) DESC
        """)
    List<Object[]> topItemsBetween(TenantId tenantId, Instant from, Instant to, Pageable pageable);
}
