package za.co.handyflow.platform.supplychain.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.supplychain.domain.enums.InvoiceStatus;
import za.co.handyflow.platform.supplychain.domain.model.ScSupplierInvoiceLine;

import java.util.List;
import java.util.UUID;

public interface ScSupplierInvoiceLineRepository extends JpaRepository<ScSupplierInvoiceLine, UUID> {

    @Query("SELECT l FROM ScSupplierInvoiceLine l WHERE l.tenantId = :tenantId AND l.supplierInvoiceId = :invoiceId ORDER BY l.lineNumber")
    List<ScSupplierInvoiceLine> findByInvoice(@Param("tenantId") UUID tenantId, @Param("invoiceId") UUID invoiceId);

    /**
     * Quantity already claimed per PO line by OTHER non-cancelled invoices on the same PO.
     * Returns [poLineId, SUM(quantity)]. Derived from the invoice lines themselves rather
     * than a mutable counter on ScPoLine, so it can never drift out of sync.
     */
    @Query("""
            SELECT l.poLineId, SUM(l.quantity)
            FROM ScSupplierInvoiceLine l, ScSupplierInvoice i
            WHERE i.id = l.supplierInvoiceId
              AND i.tenantId = :tenantId AND i.purchaseOrderId = :poId
              AND i.id <> :excludeInvoiceId AND i.status <> :excludedStatus
              AND l.poLineId IS NOT NULL
            GROUP BY l.poLineId
            """)
    List<Object[]> sumPriorInvoicedByPoLine(@Param("tenantId") UUID tenantId,
                                            @Param("poId") UUID poId,
                                            @Param("excludeInvoiceId") UUID excludeInvoiceId,
                                            @Param("excludedStatus") InvoiceStatus excludedStatus);

    /** NEW (chunk 3): PO lines already billed by non-cancelled invoices — their price/removal is locked. */
    @Query("""
            SELECT DISTINCT l.poLineId
            FROM ScSupplierInvoiceLine l, ScSupplierInvoice i
            WHERE i.id = l.supplierInvoiceId
              AND i.tenantId = :tenantId AND i.purchaseOrderId = :poId
              AND i.status <> :excludedStatus AND l.poLineId IS NOT NULL
            """)
    List<UUID> findActivePoLineIds(@Param("tenantId") UUID tenantId,
                                   @Param("poId") UUID poId,
                                   @Param("excludedStatus") InvoiceStatus excludedStatus);
}
