package za.co.handyflow.platform.ap.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "ap_eft_batches")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class ApEftBatch {

    @Id private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "batch_number",    nullable = false) private String   batchNumber;
    @Column(name = "bank_account_id")                   private UUID     bankAccountId;
    private String description;
    @Column(name = "total_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;
    @Column(name = "bill_count", nullable = false) private int billCount = 0;
    @Column(nullable = false)                      private String status = "DRAFT";
    @Column(name = "payment_date")  private LocalDate paymentDate;
    @Column(name = "payment_ref")   private String    paymentRef;

    // Evidence
    @Column(name = "pop_url")         private String  popUrl;
    @Column(name = "pop_name")        private String  popName;
    @Column(name = "pop_uploaded_at") private Instant popUploadedAt;
    @Column(name = "pop_uploaded_by") private UUID    popUploadedBy;

    @Column(name = "submitted_at") private Instant submittedAt;
    @Column(name = "paid_at")      private Instant paidAt;
    @Column(name = "paid_by")      private UUID    paidBy;
    @Column(name = "created_by")   private UUID    createdBy;
    @Column(name = "created_at")   private Instant createdAt;
    @Column(name = "updated_at")   private Instant updatedAt;
    @Version private Long version;

    public static ApEftBatch create(TenantId tenantId, String batchNumber,
                                     UUID bankAccountId, String description,
                                     LocalDate paymentDate, UUID createdBy) {
        ApEftBatch b      = new ApEftBatch();
        b.tenantId        = tenantId;
        b.batchNumber     = batchNumber;
        b.bankAccountId   = bankAccountId;
        b.description     = description;
        b.paymentDate     = paymentDate;
        b.createdBy       = createdBy;
        b.status          = "DRAFT";
        b.createdAt       = Instant.now();
        b.updatedAt       = Instant.now();
        return b;
    }

    public void addBill(BigDecimal amount) {
        this.totalAmount = this.totalAmount.add(amount);
        this.billCount++;
        this.updatedAt   = Instant.now();
    }

    public void removeBill(BigDecimal amount) {
        this.totalAmount = this.totalAmount.subtract(amount).max(BigDecimal.ZERO);
        this.billCount   = Math.max(0, this.billCount - 1);
        this.updatedAt   = Instant.now();
    }

    public void submit() {
        if (!"DRAFT".equals(status))
            throw new IllegalStateException("Only DRAFT batches can be submitted");
        this.status      = "SUBMITTED";
        this.submittedAt = Instant.now();
        this.updatedAt   = Instant.now();
    }

    public void confirmPaid(String paymentRef, UUID paidBy) {
        if (!"SUBMITTED".equals(status) && !"DRAFT".equals(status))
            throw new IllegalStateException("Batch must be DRAFT or SUBMITTED to confirm payment");
        this.status     = "PAID";
        this.paymentRef = paymentRef;
        this.paidBy     = paidBy;
        this.paidAt     = Instant.now();
        this.updatedAt  = Instant.now();
    }

    public void cancel() {
        this.status    = "CANCELLED";
        this.updatedAt = Instant.now();
    }

    public void uploadPop(String url, String name, UUID uploadedBy) {
        this.popUrl        = url;
        this.popName       = name;
        this.popUploadedBy = uploadedBy;
        this.popUploadedAt = Instant.now();
        this.updatedAt     = Instant.now();
    }

    public boolean isDraft()     { return "DRAFT".equals(status); }
    public boolean isSubmitted() { return "SUBMITTED".equals(status); }
    public boolean isPaid()      { return "PAID".equals(status); }
}


// ─────────────────────────────────────────────────────────────────────────────
// ApBatchItem — separate file in production, combined here for brevity
// Save as: ap/domain/model/ApBatchItem.java
// ─────────────────────────────────────────────────────────────────────────────
// package za.co.handyflow.platform.ap.domain.model;
//
// import jakarta.persistence.*;
// import lombok.Getter;
// import lombok.NoArgsConstructor;
// import java.math.BigDecimal;
// import java.util.UUID;
//
// @Entity
// @Table(name = "ap_batch_items")
// @Getter
// @NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
// public class ApBatchItem {
//     @Id private UUID id = UUID.randomUUID();
//     @Column(name = "batch_id", nullable = false) private UUID batchId;
//     @Column(name = "bill_id",  nullable = false) private UUID billId;
//     @Column(nullable = false, precision = 15, scale = 2) private BigDecimal amount;
//
//     public static ApBatchItem of(UUID batchId, UUID billId, BigDecimal amount) {
//         ApBatchItem i = new ApBatchItem();
//         i.batchId = batchId; i.billId = billId; i.amount = amount;
//         return i;
//     }
// }
