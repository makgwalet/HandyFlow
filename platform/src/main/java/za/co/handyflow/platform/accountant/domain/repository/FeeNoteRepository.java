package za.co.handyflow.platform.accountant.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import za.co.handyflow.platform.accountant.domain.model.FeeNote;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FeeNoteRepository extends JpaRepository<FeeNote, UUID> {

    @Query("""
        SELECT f FROM AccountantFeeNote f
        WHERE f.clientId = :clientId
        ORDER BY f.invoiceDate DESC
    """)
    Page<FeeNote> findByClient(@Param("clientId") UUID clientId, Pageable pageable);

    /**
     * Debtors view — DRAFT, SENT, PARTIAL, OVERDUE (everything not PAID or WRITTEN_OFF).
     * tenantId null = all tenants (scheduler bulk processing).
     */
    @Query("""
        SELECT f FROM AccountantFeeNote f
        WHERE (:tenantId IS NULL OR f.tenantId = :tenantId)
          AND f.status NOT IN ('PAID','WRITTEN_OFF')
        ORDER BY f.dueDate ASC
    """)
    List<FeeNote> findAllUnpaid(@Param("tenantId") UUID tenantId);

    /**
     * Outstanding only (excludes DRAFT) — used by dashboard KPI and aging.
     */
    @Query("""
        SELECT f FROM AccountantFeeNote f
        WHERE (:tenantId IS NULL OR f.tenantId = :tenantId)
          AND f.status IN ('SENT','PARTIAL','OVERDUE')
        ORDER BY f.dueDate ASC
    """)
    List<FeeNote> findOutstanding(@Param("tenantId") UUID tenantId);

    /**
     * SENT/PARTIAL invoices past due — used by scheduler to mark OVERDUE.
     */
    @Query("""
        SELECT f FROM AccountantFeeNote f
        WHERE (:tenantId IS NULL OR f.tenantId = :tenantId)
          AND f.status IN ('SENT','PARTIAL')
          AND f.dueDate < :today
    """)
    List<FeeNote> findOverdue(@Param("tenantId") UUID tenantId, @Param("today") LocalDate today);

    @Query("""
        SELECT f FROM AccountantFeeNote f
        WHERE f.tenantId = :tenantId
          AND f.invoiceNumber = :number
    """)
    Optional<FeeNote> findByInvoiceNumber(@Param("tenantId") UUID tenantId,
                                          @Param("number") String number);

    @Query("""
        SELECT f FROM AccountantFeeNote f
        WHERE f.tenantId = :tenantId
          AND f.status = 'DRAFT'
        ORDER BY f.createdAt DESC
    """)
    List<FeeNote> findDrafts(@Param("tenantId") UUID tenantId);

    /**
     * NEW: fixes a real multi-tenant data-isolation gap. sendFeeNote()
     * and fileFiling() previously used plain findById() with no tenant
     * check at all — any authenticated user from ANY tenant could
     * potentially reach another tenant's fee note by guessing/enumerating
     * a UUID. This is what recordPayment() and the corrected
     * sendFeeNote() use instead. The same pattern exists on
     * AccJournalRepository/TaxDeadlineRepository (approveJournal(),
     * postJournal(), fileFiling()) — not fixed here, since those get
     * touched again for the journals-visibility work next; flagged
     * clearly rather than silently left in place.
     */
    @Query("""
        SELECT f FROM AccountantFeeNote f
        WHERE f.tenantId = :tenantId
          AND f.id = :id
    """)
    Optional<FeeNote> findByTenantIdAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);
}