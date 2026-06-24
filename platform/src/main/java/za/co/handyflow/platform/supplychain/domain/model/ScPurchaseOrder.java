package za.co.handyflow.platform.supplychain.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

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
    @Column(name = "tenant_id",        nullable = false) UUID tenantId;
    @Column(name = "order_number",     nullable = false, length = 20) String orderNumber;
    @Column(name = "requisition_id")   UUID requisitionId;
    @Column(name = "supplier_id",      nullable = false) UUID supplierId;
    @Column(name = "supplier_name",    nullable = false) String supplierName;
    @Column(name = "deliver_to_location") UUID deliverToLocation;
    @Column(name = "deliver_to_address")  String deliverToAddress;
    @Column(name = "order_date",       nullable = false) LocalDate orderDate;
    @Column(name = "required_by_date") LocalDate requiredByDate;
    @Column(name = "expected_delivery") LocalDate expectedDelivery;
    @Column(nullable = false, length = 3) String currency = "ZAR";
    @Column(name = "exchange_rate",    nullable = false, precision = 10, scale = 6) BigDecimal exchangeRate = BigDecimal.ONE;
    @Column(nullable = false, precision = 15, scale = 2) BigDecimal subtotal    = BigDecimal.ZERO;
    @Column(name = "vat_amount",       nullable = false, precision = 15, scale = 2) BigDecimal vatAmount   = BigDecimal.ZERO;
    @Column(name = "total_amount",     nullable = false, precision = 15, scale = 2) BigDecimal totalAmount = BigDecimal.ZERO;
    @Column(nullable = false, length = 20) String status = "DRAFT";
    @Column(name = "approved_by")      UUID approvedBy;
    @Column(name = "approved_by_name") String approvedByName;
    @Column(name = "approved_at")      Instant approvedAt;
    @Column(name = "sent_at")          Instant sentAt;
    @Column(name = "rejection_reason") String rejectionReason;
    @Column(name = "supplier_reference") String supplierReference;
    @Column(name = "project_ref")      String projectRef;
    String terms;
    String notes;
    @Column(name = "internal_notes")   String internalNotes;
    @Column(name = "created_by")       UUID createdBy;
    @Column(name = "created_by_name")  String createdByName;
    @Column(name = "created_at",       nullable = false) Instant createdAt;
    @Column(name = "updated_at",       nullable = false) Instant updatedAt;
    @Column(name = "cancelled_at")     Instant cancelledAt;

    public static ScPurchaseOrder create(UUID tenantId, String orderNumber,
                                         UUID supplierId, String supplierName,
                                         UUID deliverToLocation, LocalDate orderDate,
                                         LocalDate requiredByDate, String currency,
                                         String projectRef, String notes, String internalNotes,
                                         UUID createdBy, String createdByName) {
        ScPurchaseOrder po = new ScPurchaseOrder();
        po.id = UUID.randomUUID();
        po.tenantId = tenantId;
        po.orderNumber = orderNumber;
        po.supplierId = supplierId;
        po.supplierName = supplierName;
        po.deliverToLocation = deliverToLocation;
        po.orderDate = orderDate != null ? orderDate : LocalDate.now();
        po.requiredByDate = requiredByDate;
        po.currency = currency != null ? currency : "ZAR";
        po.projectRef = projectRef;
        po.notes = notes;
        po.internalNotes = internalNotes;
        po.createdBy = createdBy;
        po.createdByName = createdByName;
        po.status = "DRAFT";
        po.createdAt = Instant.now();
        po.updatedAt = Instant.now();
        return po;
    }

    public void submit() {
        if (!"DRAFT".equals(status)) throw new IllegalStateException("Only DRAFT orders can be submitted");
        this.status = "PENDING_APPROVAL"; touch();
    }
    public void approve(UUID approverId, String approverName) {
        if (!"PENDING_APPROVAL".equals(status)) throw new IllegalStateException("Not pending approval");
        this.status = "APPROVED"; this.approvedBy = approverId;
        this.approvedByName = approverName; this.approvedAt = Instant.now(); touch();
    }
    public void reject(String reason) {
        if (!"PENDING_APPROVAL".equals(status)) throw new IllegalStateException("Not pending approval");
        this.status = "DRAFT"; this.rejectionReason = reason; touch();
    }
    public void send() {
        if (!"APPROVED".equals(status)) throw new IllegalStateException("Must be APPROVED before sending");
        this.status = "SENT"; this.sentAt = Instant.now(); touch();
    }

    /**
     * Updates the PO totals after lines are added/removed.
     * Called by ScmService after saving a new ScPoLine.
     */
    public void recalculateTotals(java.math.BigDecimal subtotal,
                                  java.math.BigDecimal vatAmount,
                                  java.math.BigDecimal totalAmount) {
        this.subtotal    = subtotal;
        this.vatAmount   = vatAmount;
        this.totalAmount = totalAmount;
        touch();
    }

    public void partiallyReceive() { this.status = "PARTIALLY_RECEIVED"; touch(); }
    public void fullyReceive()     { this.status = "FULLY_RECEIVED";     touch(); }
    public boolean canReceive() {
        return "APPROVED".equals(status) || "SENT".equals(status)
                || "ACKNOWLEDGED".equals(status) || "PARTIALLY_RECEIVED".equals(status);
    }
    private void touch() { this.updatedAt = Instant.now(); }
}
