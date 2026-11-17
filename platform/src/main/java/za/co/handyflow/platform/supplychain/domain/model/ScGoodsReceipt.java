package za.co.handyflow.platform.supplychain.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "sc_goods_receipts")
@Getter
@NoArgsConstructor
public class ScGoodsReceipt {

    public static final String DRAFT = "DRAFT";
    public static final String POSTED = "POSTED";
    public static final String REVERSED = "REVERSED";

    @Id UUID id;
    @Column(name = "tenant_id",        nullable = false) UUID tenantId;
    @Column(name = "receipt_number",   nullable = false, length = 20) String receiptNumber;
    @Column(name = "purchase_order_id", nullable = false) UUID purchaseOrderId;
    @Column(name = "supplier_id",      nullable = false) UUID supplierId;
    @Column(name = "received_to",      nullable = false) UUID receivedTo;
    @Column(name = "delivery_note_ref") String deliveryNoteRef;
    @Column(name = "received_by")      UUID receivedBy;
    @Column(name = "received_by_name") String receivedByName;
    @Column(name = "received_date",    nullable = false) LocalDate receivedDate;
    @Column(nullable = false, length = 20) String status = DRAFT;
    @Column(name = "posted_at")        Instant postedAt;
    // NEW (chunk 2): reversal audit trail. A posted GR is never edited or deleted —
    // it is reversed, with who/when/why recorded here and offsetting stock movements.
    @Column(name = "reversed_at")      Instant reversedAt;
    @Column(name = "reversed_by")      UUID reversedBy;
    @Column(name = "reversal_reason", length = 500) String reversalReason;
    String notes;
    @Column(name = "created_at")       Instant createdAt;
    @Column(name = "updated_at")       Instant updatedAt;

    public static ScGoodsReceipt create(UUID tenantId, String receiptNumber,
                                        UUID purchaseOrderId, UUID supplierId,
                                        UUID receivedTo, String deliveryNoteRef,
                                        UUID receivedBy, String receivedByName,
                                        LocalDate receivedDate, String notes) {
        ScGoodsReceipt gr = new ScGoodsReceipt();
        gr.id = UUID.randomUUID();
        gr.tenantId = tenantId;
        gr.receiptNumber = receiptNumber;
        gr.purchaseOrderId = purchaseOrderId;
        gr.supplierId = supplierId;
        gr.receivedTo = receivedTo;
        gr.deliveryNoteRef = deliveryNoteRef;
        gr.receivedBy = receivedBy;
        gr.receivedByName = receivedByName;
        gr.receivedDate = receivedDate != null ? receivedDate : LocalDate.now();
        gr.notes = notes;
        gr.status = DRAFT;
        gr.createdAt = Instant.now();
        gr.updatedAt = Instant.now();
        return gr;
    }

    public void post() {
        if (!DRAFT.equals(status)) throw new IllegalStateException("GR already " + status);
        this.status = POSTED;
        this.postedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void reverse(UUID reversedBy, String reason) {
        if (!POSTED.equals(status))
            throw new IllegalStateException("Only POSTED goods receipts can be reversed — current: " + status);
        if (reason == null || reason.isBlank())
            throw new IllegalArgumentException("A reversal reason is required");
        this.status = REVERSED;
        this.reversedAt = Instant.now();
        this.reversedBy = reversedBy;
        this.reversalReason = reason.length() > 500 ? reason.substring(0, 500) : reason;
        this.updatedAt = Instant.now();
    }
}
