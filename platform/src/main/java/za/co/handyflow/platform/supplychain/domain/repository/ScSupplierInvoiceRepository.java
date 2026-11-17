package za.co.handyflow.platform.supplychain.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.supplychain.domain.enums.InvoiceStatus;
import za.co.handyflow.platform.supplychain.domain.model.ScSupplierInvoice;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScSupplierInvoiceRepository extends JpaRepository<ScSupplierInvoice, UUID> {

    @Query("SELECT i FROM ScSupplierInvoice i WHERE i.tenantId = :tenantId ORDER BY i.createdAt DESC")
    Page<ScSupplierInvoice> findByTenantId(@Param("tenantId") UUID tenantId, Pageable pageable);

    @Query("SELECT i FROM ScSupplierInvoice i WHERE i.tenantId = :tenantId AND i.status = :status ORDER BY i.createdAt DESC")
    Page<ScSupplierInvoice> findByTenantIdAndStatus(@Param("tenantId") UUID tenantId,
                                                    @Param("status") InvoiceStatus status,
                                                    Pageable pageable);

    @Query("SELECT i FROM ScSupplierInvoice i WHERE i.tenantId = :tenantId AND i.id = :id")
    Optional<ScSupplierInvoice> findByTenantIdAndId(@Param("tenantId") UUID tenantId,
                                                    @Param("id") UUID id);

    @Query("SELECT COUNT(i) FROM ScSupplierInvoice i WHERE i.tenantId = :tenantId AND i.status = :status")
    long countByTenantIdAndStatus(@Param("tenantId") UUID tenantId,
                                  @Param("status") InvoiceStatus status);

    /** Scalar COUNT for the dashboard summary (FIX C-2 — replaces findOverdue().size()). */
    @Query("SELECT COUNT(i) FROM ScSupplierInvoice i WHERE i.tenantId = :tenantId AND i.status NOT IN ('PAID','CANCELLED') AND i.dueDate < CURRENT_DATE")
    long countOverdue(@Param("tenantId") UUID tenantId);

    /** Full list for the overdue invoice view. */
    @Query("SELECT i FROM ScSupplierInvoice i WHERE i.tenantId = :tenantId AND i.status NOT IN ('PAID','CANCELLED') AND i.dueDate < CURRENT_DATE ORDER BY i.dueDate ASC")
    List<ScSupplierInvoice> findOverdue(@Param("tenantId") UUID tenantId);

    /**
     * Cross-tenant overdue query for the daily notification scheduler.
     * Returns [tenantId, invoiceNumber, supplierName, dueDate]. Native because
     * ScSupplierInvoice stores supplierId (UUID), not a @ManyToOne.
     */
    @Query(value = """
            SELECT i.tenant_id, i.invoice_number, s.name, i.due_date
            FROM sc_supplier_invoices i
            JOIN sc_suppliers s ON s.id = i.supplier_id AND s.deleted_at IS NULL
            WHERE i.status NOT IN ('PAID','CANCELLED')
              AND i.due_date < CURRENT_DATE
            ORDER BY i.tenant_id, i.due_date
            """, nativeQuery = true)
    List<Object[]> findAllOverdueGroupedByTenant();

    // ── NEW: duplicate detection + matching support ─────────────────────────

    /** Hard duplicate: same supplier + same supplier invoice ref (case/whitespace-insensitive), not cancelled. */
    @Query("""
            SELECT COUNT(i) FROM ScSupplierInvoice i
            WHERE i.tenantId = :tenantId AND i.supplierId = :supplierId
              AND LOWER(TRIM(i.supplierInvoiceRef)) = LOWER(TRIM(:ref))
              AND i.status <> :excludedStatus
            """)
    long countActiveWithRef(@Param("tenantId") UUID tenantId,
                            @Param("supplierId") UUID supplierId,
                            @Param("ref") String ref,
                            @Param("excludedStatus") InvoiceStatus excludedStatus);

    /** Soft duplicate: same supplier, same total, same invoice date under a different ref. */
    @Query("""
            SELECT COUNT(i) FROM ScSupplierInvoice i
            WHERE i.tenantId = :tenantId AND i.supplierId = :supplierId
              AND i.totalAmount = :total AND i.invoiceDate = :invoiceDate
              AND i.id <> :excludeInvoiceId AND i.status <> :excludedStatus
            """)
    long countSameAmountAndDate(@Param("tenantId") UUID tenantId,
                                @Param("supplierId") UUID supplierId,
                                @Param("total") BigDecimal total,
                                @Param("invoiceDate") LocalDate invoiceDate,
                                @Param("excludeInvoiceId") UUID excludeInvoiceId,
                                @Param("excludedStatus") InvoiceStatus excludedStatus);

    /** Total already invoiced against a PO by other non-cancelled invoices (null when none). */
    @Query("""
            SELECT SUM(i.totalAmount) FROM ScSupplierInvoice i
            WHERE i.tenantId = :tenantId AND i.purchaseOrderId = :poId
              AND i.id <> :excludeInvoiceId AND i.status <> :excludedStatus
            """)
    BigDecimal sumInvoicedForPo(@Param("tenantId") UUID tenantId,
                                @Param("poId") UUID poId,
                                @Param("excludeInvoiceId") UUID excludeInvoiceId,
                                @Param("excludedStatus") InvoiceStatus excludedStatus);
}
