package za.co.handyflow.platform.pos.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.pos.domain.model.PosTransaction;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
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

    @Query("""
        SELECT COALESCE(SUM(t.totalAmount), 0) FROM PosTransaction t
        WHERE t.tenantId = :tenantId
        AND t.status = 'COMPLETED'
        AND t.createdAt BETWEEN :from AND :to
        """)
    BigDecimal sumSalesBetween(TenantId tenantId, Instant from, Instant to);

    @Query("""
        SELECT COUNT(t) FROM PosTransaction t
        WHERE t.tenantId = :tenantId
        AND t.status = 'COMPLETED'
        AND t.createdAt BETWEEN :from AND :to
        """)
    long countSalesBetween(TenantId tenantId, Instant from, Instant to);
}
