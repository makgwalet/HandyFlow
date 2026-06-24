package za.co.handyflow.platform.supplychain.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "sc_supplier_invoices")
@Getter
@NoArgsConstructor
public class ScSupplierInvoice {

    @Id UUID id;
    @Column(name = "tenant_id",            nullable = false) UUID tenantId;
    @Column(name = "invoice_number",       nullable = false) String invoiceNumber;
    @Column(name = "supplier_invoice_ref")                   String supplierInvoiceRef;
    @Column(name = "supplier_id",          nullable = false) UUID supplierId;
    @Column(name = "purchase_order_id")                      UUID purchaseOrderId;
    @Column(name = "goods_receipt_id")                       UUID goodsReceiptId;
    @Column(name = "invoice_date",         nullable = false) LocalDate invoiceDate;
    @Column(name = "due_date",             nullable = false) LocalDate dueDate;
    @Column(name = "received_date",        nullable = false) LocalDate receivedDate;
    @Column(nullable = false, length = 3) String currency = "ZAR";
    @Column(nullable = false, precision = 15, scale = 2) BigDecimal subtotal;
    @Column(name = "vat_amount",   nullable = false, precision = 15, scale = 2) BigDecimal vatAmount   = BigDecimal.ZERO;
    @Column(name = "total_amount", nullable = false, precision = 15, scale = 2) BigDecimal totalAmount;
    @Column(name = "match_status", nullable = false, length = 20) String matchStatus = "PENDING";
    @Column(name = "match_notes")                            String matchNotes;
    @Column(nullable = false, length = 20) String status = "RECEIVED";
    @Column(name = "approved_by")                            UUID approvedBy;
    @Column(name = "approved_by_name")                       String approvedByName;
    @Column(name = "approved_at")                            Instant approvedAt;
    @Column(name = "paid_at")                                Instant paidAt;
    @Column(name = "payment_reference")                      String paymentReference;
    @Column(name = "journal_entry_id")                       UUID journalEntryId;
    String notes;
    @Column(name = "created_at", nullable = false) Instant createdAt;
    @Column(name = "updated_at", nullable = false) Instant updatedAt;

    public static ScSupplierInvoice create(UUID tenantId, String invoiceNumber,
                                           UUID supplierId, UUID purchaseOrderId,
                                           UUID goodsReceiptId, String supplierInvoiceRef,
                                           LocalDate invoiceDate, LocalDate dueDate,
                                           String currency, BigDecimal subtotal,
                                           BigDecimal vatAmount, BigDecimal totalAmount,
                                           String notes) {
        ScSupplierInvoice inv = new ScSupplierInvoice();
        inv.id = UUID.randomUUID();
        inv.tenantId = tenantId;
        inv.invoiceNumber = invoiceNumber;
        inv.supplierId = supplierId;
        inv.purchaseOrderId = purchaseOrderId;
        inv.goodsReceiptId = goodsReceiptId;
        inv.supplierInvoiceRef = supplierInvoiceRef;
        inv.invoiceDate = invoiceDate;
        inv.dueDate = dueDate;
        inv.receivedDate = LocalDate.now();
        inv.currency = currency != null ? currency : "ZAR";
        inv.subtotal = subtotal;
        inv.vatAmount = vatAmount != null ? vatAmount : BigDecimal.ZERO;
        inv.totalAmount = totalAmount;
        inv.notes = notes;
        inv.status = "RECEIVED";
        inv.matchStatus = (purchaseOrderId != null && goodsReceiptId != null) ? "MATCHED" : "PENDING";
        inv.createdAt = Instant.now();
        inv.updatedAt = Instant.now();
        return inv;
    }

    public void approve(UUID approverId, String approverName) {
        if (!"RECEIVED".equals(status) && !"UNDER_REVIEW".equals(status))
            throw new IllegalStateException("Cannot approve in state: " + status);
        this.status = "APPROVED";
        this.approvedBy = approverId;
        this.approvedByName = approverName;
        this.approvedAt = Instant.now();
        touch();
    }

    public void markPaid(String paymentReference) {
        if (!"APPROVED".equals(status))
            throw new IllegalStateException("Only APPROVED invoices can be marked paid");
        this.status = "PAID";
        this.paidAt = Instant.now();
        this.paymentReference = paymentReference;
        touch();
    }

    public boolean isOverdue() {
        return !"PAID".equals(status) && !"CANCELLED".equals(status)
                && dueDate != null && dueDate.isBefore(LocalDate.now());
    }

    private void touch() { this.updatedAt = Instant.now(); }
}
