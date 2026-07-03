package za.co.handyflow.platform.supplychain.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.supplychain.domain.enums.InvoiceStatus;
import za.co.handyflow.platform.supplychain.domain.model.ScSupplierInvoice;

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

    /**
     * Scalar COUNT for the dashboard summary (FIX C-2 — replaces findOverdue().size()).
     */
    @Query("SELECT COUNT(i) FROM ScSupplierInvoice i WHERE i.tenantId = :tenantId AND i.status NOT IN ('PAID','CANCELLED') AND i.dueDate < CURRENT_DATE")
    long countOverdue(@Param("tenantId") UUID tenantId);

    /** Full list for the overdue invoice view. */
    @Query("SELECT i FROM ScSupplierInvoice i WHERE i.tenantId = :tenantId AND i.status NOT IN ('PAID','CANCELLED') AND i.dueDate < CURRENT_DATE ORDER BY i.dueDate ASC")
    List<ScSupplierInvoice> findOverdue(@Param("tenantId") UUID tenantId);

    /**
     * Cross-tenant overdue query for the daily notification scheduler.
     * Returns [tenantId, invoiceNumber, supplierName, dueDate].
     *
     * WHY native SQL?
     * ScSupplierInvoice stores supplierId (UUID), not a @ManyToOne relationship.
     * JPQL can only join mapped entity associations. To join sc_supplier_invoices
     * to sc_suppliers for the supplier name, a native query is required.
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

    @Query("SELECT COALESCE(MAX(CAST(SUBSTRING(i.invoiceNumber, 6) AS int)), 0) FROM ScSupplierInvoice i WHERE i.tenantId = :tenantId")
    int findMaxInvoiceSequence(@Param("tenantId") UUID tenantId);
}