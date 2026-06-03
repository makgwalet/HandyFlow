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
     * All outstanding invoices for the firm — SENT, PARTIAL, or OVERDUE.
     * Powers the debtors aging view.
     * tenantId null = all tenants (used by scheduler for bulk processing).
     */
    @Query("""
        SELECT f FROM AccountantFeeNote f
        WHERE (:tenantId IS NULL OR f.tenantId = :tenantId)
          AND f.status IN ('SENT','PARTIAL','OVERDUE')
        ORDER BY f.dueDate ASC
    """)
    List<FeeNote> findOutstanding(@Param("tenantId") UUID tenantId);

    /**
     * SENT invoices whose due_date has passed — used by scheduler to mark OVERDUE.
     * tenantId null = across all tenants.
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

    /** Draft fee notes — not yet sent to client. */
    @Query("""
        SELECT f FROM AccountantFeeNote f
        WHERE f.tenantId = :tenantId
          AND f.status = 'DRAFT'
        ORDER BY f.createdAt DESC
    """)
    List<FeeNote> findDrafts(@Param("tenantId") UUID tenantId);
}
