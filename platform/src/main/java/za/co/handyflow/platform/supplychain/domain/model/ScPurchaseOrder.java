package za.co.handyflow.platform.supplychain.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.supplychain.domain.enums.PoStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "sc_purchase_orders")
@Getter
@NoArgsConstructor
public class ScPurchaseOrder {

    @Id UUID id;
    @Column(name = "tenant_id",     nullable = false)            UUID      tenantId;
    @Column(name = "order_number",  nullable = false, length = 20) String  orderNumber;
    @Column(name = "requisition_id")                              UUID      requisitionId;
    @Column(name = "supplier_id",   nullable = false)            UUID      supplierId;
    @Column(name = "supplier_name", nullable = false)            String    supplierName;
    @Column(name = "deliver_to_location")                        UUID      deliverToLocation;
    @Column(name = "deliver_to_address")                         String    deliverToAddress;
    @Column(name = "order_date",    nullable = false)            LocalDate orderDate;
    @Column(name = "required_by_date")                           LocalDate requiredByDate;
    @Column(name = "expected_delivery")                          LocalDate expectedDelivery;
    @Column(nullable = false, length = 3) String currency = "ZAR";
    @Column(name = "exchange_rate", nullable = false, precision = 10, scale = 6) BigDecimal exchangeRate = BigDecimal.ONE;
    @Column(nullable = false, precision = 15, scale = 2) BigDecimal subtotal    = BigDecimal.ZERO;
    @Column(name = "vat_amount",    nullable = false, precision = 15, scale = 2) BigDecimal vatAmount   = BigDecimal.ZERO;
    @Column(name = "total_amount",  nullable = false, precision = 15, scale = 2) BigDecimal totalAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    PoStatus status = PoStatus.DRAFT;

    @Column(name = "approved_by")       UUID    approvedBy;
    @Column(name = "approved_by_name")  String  approvedByName;
    @Column(name = "approved_at")       Instant approvedAt;
    @Column(name = "sent_at")           Instant sentAt;
    @Column(name = "rejection_reason")  String  rejectionReason;
    @Column(name = "supplier_reference") String supplierReference;
    @Column(name = "project_ref")       String  projectRef;
    String terms;
    String notes;
    @Column(name = "internal_notes")    String  internalNotes;
    @Column(name = "created_by")        UUID    createdBy;
    @Column(name = "created_by_name")   String  createdByName;
    @Column(name = "created_at", nullable = false) Instant createdAt;
    @Column(name = "updated_at", nullable = false) Instant updatedAt;
    @Column(name = "cancelled_at")      Instant cancelledAt;

    // ── Factory ───────────────────────────────────────────────────────────────

    public static ScPurchaseOrder create(UUID tenantId, String orderNumber,
                                         UUID supplierId, String supplierName,
                                         UUID deliverToLocation, LocalDate orderDate,
                                         LocalDate requiredByDate, String currency,
                                         String projectRef, String notes, String internalNotes,
                                         UUID createdBy, String createdByName) {
        ScPurchaseOrder po = new ScPurchaseOrder();
        po.id              = UUID.randomUUID();
        po.tenantId        = tenantId;
        po.orderNumber     = orderNumber;
        po.supplierId      = supplierId;
        po.supplierName    = supplierName;
        po.deliverToLocation = deliverToLocation;
        po.orderDate       = orderDate != null ? orderDate : LocalDate.now();
        po.requiredByDate  = requiredByDate;
        po.currency        = currency != null ? currency : "ZAR";
        po.projectRef      = projectRef;
        po.notes           = notes;
        po.internalNotes   = internalNotes;
        po.createdBy       = createdBy;
        po.createdByName   = createdByName;
        po.status          = PoStatus.DRAFT;
        po.createdAt       = Instant.now();
        po.updatedAt       = Instant.now();
        return po;
    }

    // ── Lifecycle transitions ─────────────────────────────────────────────────

    public void submit() {
        if (status != PoStatus.DRAFT)
            throw new IllegalStateException("Only DRAFT orders can be submitted — current: " + status);
        status = PoStatus.PENDING_APPROVAL;
        touch();
    }

    public void approve(UUID approverId, String approverName) {
        if (status != PoStatus.PENDING_APPROVAL)
            throw new IllegalStateException("Only PENDING_APPROVAL orders can be approved — current: " + status);
        status         = PoStatus.APPROVED;
        approvedBy     = approverId;
        approvedByName = approverName;
        approvedAt     = Instant.now();
        touch();
    }

    public void reject(String reason) {
        if (status != PoStatus.PENDING_APPROVAL)
            throw new IllegalStateException("Only PENDING_APPROVAL orders can be rejected — current: " + status);
        status          = PoStatus.DRAFT;  // returns to DRAFT for correction
        rejectionReason = reason;
        touch();
    }

    public void send() {
        if (status != PoStatus.APPROVED)
            throw new IllegalStateException("Must be APPROVED before sending — current: " + status);
        status = PoStatus.SENT;
        sentAt = Instant.now();
        touch();
    }

    public void partiallyReceive() {
        status = PoStatus.PARTIALLY_RECEIVED;
        touch();
    }

    public void fullyReceive() {
        status = PoStatus.FULLY_RECEIVED;
        touch();
    }

    public void cancel() {
        if (status == PoStatus.FULLY_RECEIVED || status == PoStatus.INVOICED)
            throw new IllegalStateException("Cannot cancel a " + status + " order");
        status      = PoStatus.CANCELLED;
        cancelledAt = Instant.now();
        touch();
    }

    /**
     * Updates PO totals after lines are added or removed.
     * Called by ScmService after each ScPoLine save.
     */
    public void recalculateTotals(BigDecimal subtotal, BigDecimal vatAmount, BigDecimal totalAmount) {
        this.subtotal    = subtotal;
        this.vatAmount   = vatAmount;
        this.totalAmount = totalAmount;
        touch();
    }

    /** True if goods can still be received against this PO. */
    public boolean canReceive() {
        return status == PoStatus.APPROVED
                || status == PoStatus.SENT
                || status == PoStatus.ACKNOWLEDGED
                || status == PoStatus.PARTIALLY_RECEIVED;
    }

    private void touch() { updatedAt = Instant.now(); }
}