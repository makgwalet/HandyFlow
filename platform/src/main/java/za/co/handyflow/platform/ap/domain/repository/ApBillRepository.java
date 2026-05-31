package za.co.handyflow.platform.ap.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.ap.domain.model.ApBill;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApBillRepository extends JpaRepository<ApBill, UUID> {

    @Query("""
        SELECT b FROM ApBill b
        WHERE b.tenantId = :tenantId
        AND b.deletedAt IS NULL
        AND (:status IS NULL OR b.status = :status)
        ORDER BY b.dueDate ASC, b.createdAt DESC
        """)
    Page<ApBill> findAll(TenantId tenantId, String status, Pageable pageable);

    Optional<ApBill> findByIdAndTenantId(UUID id, TenantId tenantId);

    @Query("""
        SELECT b FROM ApBill b
        WHERE b.tenantId = :tenantId
        AND b.status = 'APPROVED'
        AND b.dueDate < :today
        AND b.deletedAt IS NULL
        """)
    List<ApBill> findOverdue(TenantId tenantId, LocalDate today);

    @Query("""
        SELECT b FROM ApBill b
        WHERE b.status IN ('APPROVED', 'OVERDUE')
        AND b.dueDate < :today
        AND b.deletedAt IS NULL
        """)
    List<ApBill> findAllOverdueAcrossTenants(LocalDate today);

    boolean existsByTenantIdAndBillNumber(TenantId tenantId, String billNumber);

    @Query("""
        SELECT COUNT(b) FROM ApBill b
        WHERE b.tenantId = :tenantId
        AND b.status = :status
        AND b.deletedAt IS NULL
        """)
    long countByStatus(TenantId tenantId, String status);

    @Query("""
        SELECT COALESCE(SUM(b.totalAmount), 0) FROM ApBill b
        WHERE b.tenantId = :tenantId
        AND b.status IN ('APPROVED', 'OVERDUE')
        AND b.deletedAt IS NULL
        """)
    BigDecimal sumOutstanding(TenantId tenantId);

    @Query("""
        SELECT COALESCE(SUM(b.totalAmount), 0) FROM ApBill b
        WHERE b.tenantId = :tenantId
        AND b.status IN ('APPROVED', 'OVERDUE')
        AND b.dueDate BETWEEN :from AND :to
        AND b.deletedAt IS NULL
        """)
    BigDecimal sumDueBetween(TenantId tenantId, LocalDate from, LocalDate to);
}
