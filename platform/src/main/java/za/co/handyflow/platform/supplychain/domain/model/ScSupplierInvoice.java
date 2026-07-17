package za.co.handyflow.platform.supplychain.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.supplychain.domain.enums.InvoiceStatus;
import za.co.handyflow.platform.supplychain.domain.enums.MatchStatus;

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
    @Column(name = "tenant_id",          nullable = false) UUID      tenantId;
    @Column(name = "invoice_number",     nullable = false) String    invoiceNumber;
    @Column(name = "supplier_invoice_ref")                 String    supplierInvoiceRef;
    @Column(name = "supplier_id",        nullable = false) UUID      supplierId;
    @Column(name = "purchase_order_id")                    UUID      purchaseOrderId;
    @Column(name = "goods_receipt_id")                     UUID      goodsReceiptId;
    @Column(name = "invoice_date",       nullable = false) LocalDate invoiceDate;
    @Column(name = "due_date",           nullable = false) LocalDate dueDate;
    @Column(name = "received_date",      nullable = false) LocalDate receivedDate;
    @Column(nullable = false, length = 3) String currency = "ZAR";
    @Column(nullable = false, precision = 15, scale = 2) BigDecimal subtotal;
    @Column(name = "vat_amount",   nullable = false, precision = 15, scale = 2) BigDecimal vatAmount   = BigDecimal.ZERO;
    @Column(name = "total_amount", nullable = false, precision = 15, scale = 2) BigDecimal totalAmount;

    /**
     * Three-way match result — set by ScmService.performThreeWayMatch().
     * This is NOT the same as invoice approval status.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "match_status", nullable = false, length = 20)
    MatchStatus matchStatus = MatchStatus.PENDING;

    @Column(name = "match_notes") String matchNotes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    InvoiceStatus status = InvoiceStatus.RECEIVED;

    @Column(name = "approved_by")       UUID    approvedBy;
    @Column(name = "approved_by_name")  String  approvedByName;
    @Column(name = "approved_at")       Instant approvedAt;
    @Column(name = "paid_at")           Instant paidAt;
    @Column(name = "payment_reference") String  paymentReference;
    @Column(name = "journal_entry_id")  UUID    journalEntryId;
    String notes;
    @Column(name = "created_at", nullable = false) Instant createdAt;
    @Column(name = "updated_at", nullable = false) Instant updatedAt;

    // ── Factory ───────────────────────────────────────────────────────────────

    /**
     * Creates a new supplier invoice.
     * matchStatus is set to PENDING — the caller must invoke
     * ScmService.performThreeWayMatch() to evaluate and update it.
     */
    public static ScSupplierInvoice create(UUID tenantId, String invoiceNumber,
                                           UUID supplierId, UUID purchaseOrderId,
                                           UUID goodsReceiptId, String supplierInvoiceRef,
                                           LocalDate invoiceDate, LocalDate dueDate,
                                           String currency, BigDecimal subtotal,
                                           BigDecimal vatAmount, BigDecimal totalAmount,
                                           String notes) {
        ScSupplierInvoice inv = new ScSupplierInvoice();
        inv.id                 = UUID.randomUUID();
        inv.tenantId           = tenantId;
        inv.invoiceNumber      = invoiceNumber;
        inv.supplierId         = supplierId;
        inv.purchaseOrderId    = purchaseOrderId;
        inv.goodsReceiptId     = goodsReceiptId;
        inv.supplierInvoiceRef = supplierInvoiceRef;
        inv.invoiceDate        = invoiceDate;
        inv.dueDate            = dueDate;
        inv.receivedDate       = LocalDate.now();
        inv.currency           = currency != null ? currency : "ZAR";
        inv.subtotal           = subtotal;
        inv.vatAmount          = vatAmount != null ? vatAmount : BigDecimal.ZERO;
        inv.totalAmount        = totalAmount;
        inv.notes              = notes;
        inv.status             = InvoiceStatus.RECEIVED;
        inv.matchStatus        = MatchStatus.PENDING;  // always start PENDING — match evaluated separately
        inv.createdAt          = Instant.now();
        inv.updatedAt          = Instant.now();
        return inv;
    }

    // ── Lifecycle transitions ─────────────────────────────────────────────────

    public void approve(UUID approverId, String approverName) {
        if (status != InvoiceStatus.RECEIVED && status != InvoiceStatus.UNDER_REVIEW)
            throw new IllegalStateException("Cannot approve invoice in state: " + status);
        status         = InvoiceStatus.APPROVED;
        approvedBy     = approverId;
        approvedByName = approverName;
        approvedAt     = Instant.now();
        touch();
    }

    public void dispute(String reason) {
        if (status == InvoiceStatus.PAID || status == InvoiceStatus.CANCELLED)
            throw new IllegalStateException("Cannot dispute invoice in state: " + status);
        status      = InvoiceStatus.DISPUTED;
        matchNotes  = reason;
        matchStatus = MatchStatus.DISPUTE;
        touch();
    }

    /**
     * NEW (Tier 1 gap analysis): previously a DISPUTED invoice was a
     * genuine dead end — approve() only accepts RECEIVED/UNDER_REVIEW, so
     * a disputed invoice could never be approved even if a manager
     * decided the variance was acceptable. MatchStatus.OVERRIDDEN already
     * existed in the enum specifically for this ("manager forced approval
     * despite mismatch — reason in match_notes") but nothing ever set it.
     * The override reason is appended to matchNotes rather than replacing
     * it, so the original dispute reason stays in the audit trail
     * alongside why it was overridden.
     */
    public void overrideDisputeAndApprove(UUID approverId, String approverName, String overrideReason) {
        if (status != InvoiceStatus.DISPUTED)
            throw new IllegalStateException("Only DISPUTED invoices can be override-approved — current: " + status);
        matchStatus    = MatchStatus.OVERRIDDEN;
        matchNotes     = (matchNotes != null ? matchNotes + " | " : "") + "Overridden: " + overrideReason;
        status         = InvoiceStatus.APPROVED;
        approvedBy     = approverId;
        approvedByName = approverName;
        approvedAt     = Instant.now();
        touch();
    }

    /**
     * NEW (Tier 1 gap analysis): there was previously no way to cancel a
     * supplier invoice in ANY status — not just DISPUTED ones. Needed as
     * the second real resolution option for a dispute (reject the invoice
     * back to the supplier, as opposed to overriding and paying it
     * anyway), but written generally since the gap applied to every
     * status, not only DISPUTED.
     */
    public void cancel(String reason) {
        if (status == InvoiceStatus.PAID)
            throw new IllegalStateException("Cannot cancel a PAID invoice");
        status     = InvoiceStatus.CANCELLED;
        matchNotes = (matchNotes != null ? matchNotes + " | " : "") + "Cancelled: " + reason;
        touch();
    }

    public void markPaid(String paymentReference) {
        if (status != InvoiceStatus.APPROVED)
            throw new IllegalStateException("Only APPROVED invoices can be marked paid — current: " + status);
        status           = InvoiceStatus.PAID;
        paidAt           = Instant.now();
        this.paymentReference = paymentReference;
        touch();
    }

    /** Called by ScmService.performThreeWayMatch() after evaluating the match. */
    public void updateMatchResult(MatchStatus matchStatus, String notes) {
        this.matchStatus = matchStatus;
        this.matchNotes  = notes;
        touch();
    }

    /** True if this invoice is past its due date and not yet paid or cancelled. */
    public boolean isOverdue() {
        return status != InvoiceStatus.PAID
                && status != InvoiceStatus.CANCELLED
                && dueDate != null
                && dueDate.isBefore(LocalDate.now());
    }

    private void touch() { updatedAt = Instant.now(); }
}