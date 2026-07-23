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

    // Only APPROVED — a DRAFT bill hasn't been cleared for payment yet
    // (reminding about it would be premature), and OVERDUE bills already
    // get separate handling via markOverdueBills()/BILL_OVERDUE. Scoped
    // across all tenants, same shape as findAllOverdueAcrossTenants above,
    // since this is called from a scheduler with no single tenant context.
    @Query("""
        SELECT b FROM ApBill b
        WHERE b.status = 'APPROVED'
        AND b.dueDate BETWEEN :today AND :windowEnd
        AND b.dueSoonReminderSentAt IS NULL
        AND b.deletedAt IS NULL
        """)
    List<ApBill> findDueSoonAcrossTenants(LocalDate today, LocalDate windowEnd);

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

    // Same status filter as sumOutstanding() above, just returning full
    // records instead of a sum — used by the AP aging report, which needs
    // every bill's own due date to bucket it, not just a total.
    @Query("""
        SELECT b FROM ApBill b
        WHERE b.tenantId = :tenantId
        AND b.status IN ('APPROVED', 'OVERDUE')
        AND b.deletedAt IS NULL
        ORDER BY b.dueDate ASC
        """)
    List<ApBill> findAllOutstanding(TenantId tenantId);

    /**
     * Possible-duplicate check for createBill() — a WARNING signal, not a
     * hard block. Deliberately keyed on supplier + amount + a tight date
     * window (not exact bill number, which is already checked separately
     * and hard-blocked via existsByTenantIdAndBillNumber). A ±10 day
     * window is meant to catch accidental double-entry (the same invoice
     * re-keyed under a typo'd reference) without flagging genuinely
     * recurring bills — monthly rent or salaries at the identical amount
     * are ~30 days apart, well outside this window, so they won't
     * trigger it. CANCELLED bills are excluded — a cancelled duplicate
     * isn't a live problem.
     */
    @Query("""
        SELECT b FROM ApBill b
        WHERE b.tenantId = :tenantId
        AND b.deletedAt IS NULL
        AND b.status != 'CANCELLED'
        AND LOWER(b.supplierName) = LOWER(:supplierName)
        AND b.totalAmount = :totalAmount
        AND b.billDate BETWEEN :from AND :to
        ORDER BY b.billDate DESC
        """)
    List<ApBill> findPossibleDuplicates(TenantId tenantId, String supplierName,
                                        BigDecimal totalAmount, LocalDate from, LocalDate to);

    // For the supplier banking UI's name picker — avoids someone having
    // to retype a supplier name from memory and risk a typo that would
    // silently break the case-insensitive match in
    // ApSupplierBankingRepository.findByTenantIdAndSupplierName().
    @Query("""
        SELECT DISTINCT b.supplierName FROM ApBill b
        WHERE b.tenantId = :tenantId
        AND b.deletedAt IS NULL
        ORDER BY b.supplierName ASC
        """)
    List<String> findDistinctSupplierNames(TenantId tenantId);

    @Query("""
        SELECT COALESCE(SUM(b.totalAmount), 0) FROM ApBill b
        WHERE b.tenantId = :tenantId
        AND b.status IN ('APPROVED', 'OVERDUE')
        AND b.dueDate BETWEEN :from AND :to
        AND b.deletedAt IS NULL
        """)
    BigDecimal sumDueBetween(TenantId tenantId, LocalDate from, LocalDate to);
}